/*******************************************************************************
 * Software Name : RCS IMS Stack
 *
 * Copyright (C) 2010 France Telecom S.A.
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
 ******************************************************************************/

package com.orangelabs.rcs.service.api;

import android.os.RemoteCallbackList;

import com.gsma.services.rcs.extension.IMultimediaStreamingSession;
import com.gsma.services.rcs.extension.IMultimediaStreamingSessionListener;
import com.gsma.services.rcs.extension.MultimediaSession;
import com.orangelabs.rcs.core.ims.protocol.sip.SipDialogPath;
import com.orangelabs.rcs.core.ims.service.ImsServiceSession;
import com.orangelabs.rcs.core.ims.service.sip.SipSessionError;
import com.orangelabs.rcs.core.ims.service.sip.SipSessionListener;
import com.orangelabs.rcs.core.ims.service.sip.streaming.GenericSipRtpSession;
import com.orangelabs.rcs.core.ims.service.sip.streaming.OriginatingSipRtpSession;
import com.orangelabs.rcs.utils.PhoneUtils;
import com.orangelabs.rcs.utils.logger.Logger;

/**
 * Multimedia streaming session
 *
 * @author Jean-Marc AUFFRET
 */
public class MultimediaStreamingSessionImpl extends IMultimediaStreamingSession.Stub implements SipSessionListener {

	/**
	 * Core session
	 */
	private GenericSipRtpSession session;

	/**
	 * List of listeners for streaming session
	 */
	private RemoteCallbackList<IMultimediaStreamingSessionListener> listeners = new RemoteCallbackList<IMultimediaStreamingSessionListener>();

	/**
	 * Lock used for synchronisation
	 */
	private Object lock = new Object();
	
    /**
	 * The logger
	 */
	private Logger logger = Logger.getLogger(this.getClass().getName());

    /**
     * Constructor
     *
     * @param session Session
     */
	public MultimediaStreamingSessionImpl(GenericSipRtpSession session) {
		this.session = session;
		
		session.addListener(this);
	}

    /**
	 * Returns the session ID of the multimedia session
	 * 
	 * @return Session ID
	 */
	public String getSessionId() {
		return session.getSessionID();
	}

	/**
	 * Returns the remote contact
	 * 
	 * @return Contact
	 */
	public String getRemoteContact() {
		return PhoneUtils.extractNumberFromUri(session.getRemoteContact());
	}
	
	/**
	 * Returns the state of the session
	 * 
	 * @return State
	 */
	public int getState() {
		int result = MultimediaSession.State.INACTIVE;
		SipDialogPath dialogPath = session.getDialogPath();
		if (dialogPath != null) {
			if (dialogPath.isSessionCancelled()) {
				// Session canceled
				result = MultimediaSession.State.ABORTED;
			} else
			if (dialogPath.isSessionEstablished()) {
				// Session started
				result = MultimediaSession.State.STARTED;
			} else
			if (dialogPath.isSessionTerminated()) {
				// Session terminated
				result = MultimediaSession.State.TERMINATED;
			} else {
				// Session pending
				if (session instanceof OriginatingSipRtpSession) {
					result = MultimediaSession.State.INITIATED;
				} else {
					result = MultimediaSession.State.INVITED;
				}
			}
		}
		return result;	
	}	

	/**
	 * Returns the direction of the session (incoming or outgoing)
	 * 
	 * @return Direction
	 * @see MultimediaSession.Direction
	 */
	public int getDirection() {
		if (session instanceof OriginatingSipRtpSession) {
			return MultimediaSession.Direction.OUTGOING;
		} else {
			return MultimediaSession.Direction.INCOMING;
		}
	}		
	
	/**
	 * Returns the service ID
	 * 
	 * @return Service ID
	 */
	public String getServiceId() {
		return session.getServiceId();
	}	
	
	/**
	 * Accepts session invitation
	 */
	public void acceptInvitation() {
		if (logger.isActivated()) {
			logger.info("Accept session invitation");
		}
		
		// Accept invitation
        Thread t = new Thread() {
    		public void run() {
    			session.acceptSession();
    		}
    	};
    	t.start();
	}

	/**
	 * Rejects session invitation
	 */
	public void rejectInvitation() {
		if (logger.isActivated()) {
			logger.info("Reject session invitation");
		}

		// Reject invitation
        Thread t = new Thread() {
    		public void run() {
    			session.rejectSession(603);
    		}
    	};
    	t.start();
    }

	/**
	 * Aborts the session
	 */
	public void abortSession() {
		if (logger.isActivated()) {
			logger.info("Cancel session");
		}

		// Abort the session
        Thread t = new Thread() {
    		public void run() {
    			session.abortSession(ImsServiceSession.TERMINATION_BY_USER);
    		}
    	};
    	t.start();
	}

	/**
	 * Adds a listener on streaming session events
	 * 
	 * @param listener Session event listener
	 */
	public void addEventListener(IMultimediaStreamingSessionListener listener) {
		if (logger.isActivated()) {
			logger.info("Add an event listener");
		}

    	synchronized(lock) {
    		listeners.register(listener);
    	}
	}

	/**
	 * Removes a listener on messaging session events
	 * 
	 * @param listener Session event listener
	 */
	public void removeEventListener(IMultimediaStreamingSessionListener listener) {
		if (logger.isActivated()) {
			logger.info("Remove an event listener");
		}

    	synchronized(lock) {
    		listeners.unregister(listener);
    	}
	}

    /**
     * Sends a payload in real time
     * 
     * @param content Payload content
	 * @return Returns true if sent successfully else returns false
     */
    public boolean sendPayload(byte[] content) {
    	if (session != null) {
    		return session.sendPlayload(content);
    	} else {
    		return false;	
    	}
    }	
	
    /*------------------------------- SESSION EVENTS ----------------------------------*/

	/**
	 * Session is started
	 */
    public void handleSessionStarted() {
    	synchronized(lock) {
			if (logger.isActivated()) {
				logger.info("Session started");
			}
	
	  		// Notify event listeners
			final int N = listeners.beginBroadcast();
	        for (int i=0; i < N; i++) {
	            try {
	            	listeners.getBroadcastItem(i).onSessionStarted();
	            } catch(Exception e) {
	            	if (logger.isActivated()) {
	            		logger.error("Can't notify listener", e);
	            	}
	            }
	        }
	        listeners.finishBroadcast();
	    }
    }
    
    /**
     * Session has been aborted
     * 
	 * @param reason Termination reason
     */
    public void handleSessionAborted(int reason) {
    	synchronized(lock) {
			if (logger.isActivated()) {
				logger.info("Session aborted (reason " + reason + ")");
			}
	
	  		// Notify event listeners
			final int N = listeners.beginBroadcast();
	        for (int i=0; i < N; i++) {
	            try {
	            	listeners.getBroadcastItem(i).onSessionAborted();
	            } catch(Exception e) {
	            	if (logger.isActivated()) {
	            		logger.error("Can't notify listener", e);
	            	}
	            }
	        }
	        listeners.finishBroadcast();
	        
	        // Remove session from the list
	        MultimediaSessionServiceImpl.removeStreamingSipSession(session.getSessionID());
	    }
    }
    
    /**
     * Session has been terminated by remote
     */
    public void handleSessionTerminatedByRemote() {
    	synchronized(lock) {
			if (logger.isActivated()) {
				logger.info("Session terminated by remote");
			}
	
	  		// Notify event listeners
			final int N = listeners.beginBroadcast();
	        for (int i=0; i < N; i++) {
	            try {
	            	listeners.getBroadcastItem(i).onSessionAborted();
	            } catch(Exception e) {
	            	if (logger.isActivated()) {
	            		logger.error("Can't notify listener", e);
	            	}
	            }
	        }
	        listeners.finishBroadcast();
	        
	        // Remove session from the list
	        MultimediaSessionServiceImpl.removeStreamingSipSession(session.getSessionID());
	    }
    }
    
    /**
     * Session error
     *
     * @param error Error
     */
    public void handleSessionError(SipSessionError error) {
    	synchronized(lock) {
			if (logger.isActivated()) {
				logger.info("Session error " + error.getErrorCode());
			}
	
	  		// Notify event listeners
			final int N = listeners.beginBroadcast();
	        for (int i=0; i < N; i++) {
	            try {
	            	int code;
	            	switch(error.getErrorCode()) {
            			case SipSessionError.SESSION_INITIATION_DECLINED:
	            			code = MultimediaSession.Error.INVITATION_DECLINED;
	            			break;
            			case SipSessionError.MEDIA_FAILED:
	            			code = MultimediaSession.Error.MEDIA_FAILED;
	            			break;
	            		default:
	            			code = MultimediaSession.Error.SESSION_FAILED;
	            	}
	            	listeners.getBroadcastItem(i).onSessionError(code);
	            } catch(Exception e) {
	            	if (logger.isActivated()) {
	            		logger.error("Can't notify listener", e);
	            	}
	            }
	        }
	        listeners.finishBroadcast();
	        
	        // Remove session from the list
	        MultimediaSessionServiceImpl.removeStreamingSipSession(session.getSessionID());
	    }
    }
    
    /**
     * Receive data
     * 
     * @param data Data
     */
    public void handleReceiveData(byte[] data) {
    	synchronized(lock) {
	  		// Notify event listeners
			final int N = listeners.beginBroadcast();
	        for (int i=0; i < N; i++) {
	            try {
	            	listeners.getBroadcastItem(i).onNewPayload(data);
	            } catch(Exception e) {
	            	if (logger.isActivated()) {
	            		logger.error("Can't notify listener", e);
	            	}
	            }
	        }
	        listeners.finishBroadcast();
	    }  	
    }
}
