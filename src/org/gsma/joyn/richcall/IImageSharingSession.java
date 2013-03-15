/*
 * Copyright 2013, France Telecom
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gsma.joyn.richcall;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import java.lang.String;

/**
 * Interface IImageSharingSession.
 * <p>
 * File generated from AIDL
 */
public interface IImageSharingSession extends IInterface {
    /**
     * Returns the session i d.
     *
     * @return  The session i d.
     */
    public String getSessionID() throws RemoteException;

    /**
     * Returns the remote contact.
     *
     * @return  The remote contact.
     */
    public String getRemoteContact() throws RemoteException;

    /**
     * Returns the session state.
     *
     * @return  The session state.
     */
    public int getSessionState() throws RemoteException;

    /**
     * Returns the filename.
     *
     * @return  The filename.
     */
    public String getFilename() throws RemoteException;

    /**
     * Returns the filesize.
     *
     * @return  The filesize.
     */
    public long getFilesize() throws RemoteException;

    /**
     * Returns the file thumbnail.
     *
     * @return  The file thumbnail.
     */
    public String getFileThumbnail() throws RemoteException;

    public void acceptSession() throws RemoteException;

    public void rejectSession() throws RemoteException;

    public void cancelSession() throws RemoteException;

    /**
     * Adds a session listener.
     *
     * @param arg1 The arg1.
     */
    public void addSessionListener(IImageSharingEventListener arg1) throws RemoteException;

    /**
     * Removes a session listener.
     *
     * @param arg1 The arg1.
     */
    public void removeSessionListener(IImageSharingEventListener arg1) throws RemoteException;

    public abstract static class Stub extends Binder implements IImageSharingSession {

        public Stub() {
            super();
        }

        /**
         *
         * @return  The i binder.
         */
        public IBinder asBinder() {
            return (IBinder) null;
        }

        /**
         *
         * @param code
         * @param data
         * @param reply
         * @param flags
         * @return  The boolean.
         */
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            return false;
        }

        /**
         *
         * @param arg1 The arg1.
         * @return  The i image sharing session.
         */
        public static IImageSharingSession asInterface(IBinder binder) {
            return (IImageSharingSession) null;
        }

    }

}
