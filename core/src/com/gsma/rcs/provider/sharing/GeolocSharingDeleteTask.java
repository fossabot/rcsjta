
package com.gsma.rcs.provider.sharing;

import com.gsma.rcs.core.ims.service.richcall.RichcallService;
import com.gsma.rcs.core.ims.service.richcall.geoloc.GeolocTransferSession;
import com.gsma.rcs.provider.DeleteTask;
import com.gsma.rcs.provider.LocalContentResolver;
import com.gsma.rcs.service.api.GeolocSharingServiceImpl;
import com.gsma.services.rcs.contact.ContactId;

import java.util.Set;

public class GeolocSharingDeleteTask extends DeleteTask.GroupedByContactId {

    private final GeolocSharingServiceImpl mGeolocSharingService;

    private final RichcallService mRichcallService;

    /**
     * Deletion of all Geoloc sharing.
     * 
     * @param geolocSharingService the geoloc service impl
     * @param richcallService the rich call service
     * @param contentResolver the local content resolver
     * @param imsLock the ims operation lock
     */
    public GeolocSharingDeleteTask(GeolocSharingServiceImpl geolocSharingService,
            RichcallService richcallService, LocalContentResolver contentResolver, Object imsLock) {
        super(contentResolver, imsLock, GeolocSharingData.CONTENT_URI,
                GeolocSharingData.KEY_SHARING_ID, GeolocSharingData.KEY_CONTACT, null);
        mGeolocSharingService = geolocSharingService;
        mRichcallService = richcallService;
    }

    /**
     * Deletion of all geoloc sharing with a specific contact.
     * 
     * @param geolocSharingService the geoloc service impl
     * @param richcallService the rich call service
     * @param contentResolver the local content resolver
     * @param imsLock the ims operation lock
     * @param contact the contact id
     */
    public GeolocSharingDeleteTask(GeolocSharingServiceImpl geolocSharingService,
            RichcallService richcallService, LocalContentResolver contentResolver, Object imsLock,
            ContactId contact) {
        super(contentResolver, imsLock, GeolocSharingData.CONTENT_URI,
                GeolocSharingData.KEY_SHARING_ID, GeolocSharingData.KEY_CONTACT, contact);
        mGeolocSharingService = geolocSharingService;
        mRichcallService = richcallService;
    }

    /**
     * Deletion of a specific geoloc sharing.
     * 
     * @param geolocSharingService the geoloc service impl
     * @param richcallService the rich call service
     * @param contentResolver the local content resolver
     * @param imsLock the ims operation lock
     * @param transferId the transfer id
     */
    public GeolocSharingDeleteTask(GeolocSharingServiceImpl geolocSharingService,
            RichcallService richcallService, LocalContentResolver contentResolver, Object imsLock,
            String transferId) {
        super(contentResolver, imsLock, GeolocSharingData.CONTENT_URI,
                GeolocSharingData.KEY_SHARING_ID, GeolocSharingData.KEY_CONTACT, null, transferId);
        mGeolocSharingService = geolocSharingService;
        mRichcallService = richcallService;
    }

    @Override
    protected void onRowDelete(ContactId contact, String sharingId) {
        GeolocTransferSession session = mRichcallService.getGeolocTransferSession(sharingId);
        if (session == null) {
            mGeolocSharingService.removeGeolocSharing(sharingId);
            return;

        }
        session.deleteSession();
        mGeolocSharingService.removeGeolocSharing(sharingId);
    }

    @Override
    protected void onCompleted(ContactId contact, Set<String> deletedIds) {
        mGeolocSharingService.broadcastDeleted(contact, deletedIds);
    }

}
