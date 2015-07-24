/*******************************************************************************
 * Software Name : RCS IMS Stack
 *
 * Copyright (C) 2010 France Telecom S.A.
 * Copyright (C) 2015 Sony Mobile Communications Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * NOTE: This file has been modified by Sony Mobile Communications Inc.
 * Modifications are licensed under the License.
 ******************************************************************************/

package com.gsma.rcs.core.ims.service.capability;

import com.gsma.rcs.core.ims.ImsModule;
import com.gsma.rcs.core.ims.network.sip.SipUtils;
import com.gsma.rcs.core.ims.protocol.sip.SipNetworkException;
import com.gsma.rcs.core.ims.protocol.sip.SipPayloadException;
import com.gsma.rcs.core.ims.protocol.sip.SipRequest;
import com.gsma.rcs.core.ims.service.ContactInfo.RcsStatus;
import com.gsma.rcs.core.ims.service.ContactInfo.RegistrationState;
import com.gsma.rcs.core.ims.service.presence.PresenceInfo;
import com.gsma.rcs.core.ims.service.presence.PresenceUtils;
import com.gsma.rcs.core.ims.service.presence.pidf.PidfDocument;
import com.gsma.rcs.core.ims.service.presence.pidf.PidfParser;
import com.gsma.rcs.core.ims.service.presence.pidf.Tuple;
import com.gsma.rcs.provider.contact.ContactManager;
import com.gsma.rcs.provider.settings.RcsSettings;
import com.gsma.rcs.utils.ContactUtil;
import com.gsma.rcs.utils.ContactUtil.PhoneNumber;
import com.gsma.rcs.utils.logger.Logger;
import com.gsma.services.rcs.contact.ContactId;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

/**
 * Capability discovery manager using anonymous fetch procedure
 * 
 * @author Jean-Marc AUFFRET
 */
public class AnonymousFetchManager implements DiscoveryManager {
    /**
     * IMS module
     */
    private ImsModule mImsModule;

    /**
     * The logger
     */
    private Logger logger = Logger.getLogger(this.getClass().getName());

    private final RcsSettings mRcsSettings;

    private final ContactManager mContactManager;

    /**
     * Constructor
     * 
     * @param parent IMS module
     * @param rcsSettings RCS settings accessor
     * @param contactManager Contact manager accessor
     */
    public AnonymousFetchManager(ImsModule parent, RcsSettings rcsSettings,
            ContactManager contactManager) {
        mImsModule = parent;
        mRcsSettings = rcsSettings;
        mContactManager = contactManager;
    }

    /**
     * Request contact capabilities
     * 
     * @param contact Remote contact identifier
     */
    public void requestCapabilities(ContactId contact) {
        if (logger.isActivated()) {
            logger.debug("Request capabilities in background for " + contact);
        }
        AnonymousFetchRequestTask task = new AnonymousFetchRequestTask(mImsModule, contact,
                mRcsSettings, mContactManager);
        task.start();
    }

    /**
     * Receive a notification
     * 
     * @param notify Received notify
     * @throws SipPayloadException
     * @throws SipNetworkException
     */
    public void receiveNotification(SipRequest notify) throws SipPayloadException,
            SipNetworkException {
        boolean logActivated = logger.isActivated();
        if (logActivated) {
            logger.debug("Anonymous fetch notification received");
        }

        /* Parse XML part */
        byte[] content = notify.getContentBytes();
        if (content != null) {
            if (logActivated) {
                logger.debug("Anonymous fetch notification with PIDF document");
            }
            InputSource pidfInput = new InputSource(new ByteArrayInputStream(content));
            PidfParser pidfParser = null;
            try {
                pidfParser = new PidfParser(pidfInput);
            } catch (ParserConfigurationException e) {
                throw new SipPayloadException("Can't parse XML notification! CallId=".concat(notify
                        .getCallId()), e);
            } catch (SAXException e) {
                throw new SipPayloadException("Can't parse XML notification! CallId=".concat(notify
                        .getCallId()), e);
            } catch (IOException e) {
                throw new SipNetworkException("Can't parse XML notification! CallId=".concat(notify
                        .getCallId()), e);
            }
            PidfDocument presence = pidfParser.getPresence();
            if (presence == null) {
                return;
            }
            /* Extract capabilities */
            Capabilities.CapabilitiesBuilder capaBuilder = new Capabilities.CapabilitiesBuilder();

            /* We queried via anonymous fetch procedure, so set presence discovery to true */
            capaBuilder.setPresenceDiscovery(true);

            String entity = presence.getEntity();
            PhoneNumber validPhoneNumber = ContactUtil.getValidPhoneNumberFromUri(entity);
            if (validPhoneNumber == null) {
                if (logActivated) {
                    logger.error(new StringBuilder("Discard XML notification: bad entity '")
                            .append(entity).append("'").toString());
                }
                return;
            }
            ContactId contact = ContactUtil.createContactIdFromValidatedData(validPhoneNumber);
            for (Tuple tuple : presence.getTuplesList()) {
                boolean state = false;
                if (PresenceInfo.ONLINE.equals(tuple.getStatus().getBasic().getValue())) {
                    state = true;
                }
                String id = tuple.getService().getId();
                if (PresenceUtils.FEATURE_RCS2_VIDEO_SHARE.equalsIgnoreCase(id)) {
                    capaBuilder.setVideoSharing(state);

                } else if (PresenceUtils.FEATURE_RCS2_IMAGE_SHARE.equalsIgnoreCase(id)) {
                    capaBuilder.setImageSharing(state);

                } else if (PresenceUtils.FEATURE_RCS2_FT.equalsIgnoreCase(id)) {
                    capaBuilder.setFileTransferMsrp(state);

                } else if (PresenceUtils.FEATURE_RCS2_CS_VIDEO.equalsIgnoreCase(id)) {
                    capaBuilder.setCsVideo(state);

                } else if (PresenceUtils.FEATURE_RCS2_CHAT.equalsIgnoreCase(id)) {
                    capaBuilder.setImSession(state);
                }
            }

            Capabilities capabilities = capaBuilder.build();
            mContactManager.setContactCapabilities(contact, capabilities, RcsStatus.RCS_CAPABLE,
                    RegistrationState.UNKNOWN);

            mImsModule.getCore().getListener()
                    .handleCapabilitiesNotification(contact, capabilities);
        } else {
            if (logActivated) {
                logger.debug("Anonymous fetch notification is empty");
            }
            String sipAssertedId = SipUtils.getAssertedIdentity(notify);
            PhoneNumber validPhoneNumber = ContactUtil.getValidPhoneNumberFromUri(sipAssertedId);
            if (validPhoneNumber == null) {
                if (logActivated) {
                    logger.error(new StringBuilder("Cannot process notification: invalid SIP id '")
                            .append(sipAssertedId).append("'").toString());
                }
                return;
            }
            ContactId contact = ContactUtil.createContactIdFromValidatedData(validPhoneNumber);

            /* Notify content was empty : set default capabilities */
            mContactManager.setContactCapabilities(contact, Capabilities.sDefaultCapabilities,
                    RcsStatus.NO_INFO, RegistrationState.UNKNOWN);

            mImsModule.getCore().getListener()
                    .handleCapabilitiesNotification(contact, Capabilities.sDefaultCapabilities);
        }
    }
}
