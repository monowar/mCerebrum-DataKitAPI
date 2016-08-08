package org.md2k.datakitapi;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import org.md2k.datakitapi.datatype.DataType;
import org.md2k.datakitapi.datatype.DataTypeDoubleArray;
import org.md2k.datakitapi.datatype.DataTypeLong;
import org.md2k.datakitapi.datatype.RowObject;
import org.md2k.datakitapi.exception.DataKitException;
import org.md2k.datakitapi.exception.DataKitNotFoundException;
import org.md2k.datakitapi.messagehandler.MessageType;
import org.md2k.datakitapi.messagehandler.OnConnectionListener;
import org.md2k.datakitapi.messagehandler.OnReceiveListener;
import org.md2k.datakitapi.messagehandler.PendingResult;
import org.md2k.datakitapi.source.METADATA;
import org.md2k.datakitapi.source.application.Application;
import org.md2k.datakitapi.source.application.ApplicationBuilder;
import org.md2k.datakitapi.source.datasource.DataSource;
import org.md2k.datakitapi.source.datasource.DataSourceBuilder;
import org.md2k.datakitapi.source.datasource.DataSourceClient;
import org.md2k.datakitapi.status.Status;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/*
 * Copyright (c) 2015, The University of Memphis, MD2K Center
 * - Syed Monowar Hossain <monowar.hossain@gmail.com>
 * - Timothy W. Hnat <twhnat@memphis.edu>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
class DataKitAPIExecute {
    private static final String TAG = DataKitAPIExecute.class.getSimpleName();
    private boolean isConnected;
    int sessionId = -1;

    private DataType subscribedData;
    private ArrayList<RowObject> queryHFPrimaryKeyData;
    private ArrayList<RowObject> queryPrimaryKeyData;
    private DataTypeLong querySizeData;
    private ArrayList<DataType> queryHFLastNData;
    private ArrayList<DataType> queryData;
    private Status unregisterData;
    private Status unsubscribeData;
    private ArrayList<DataSourceClient> findData;
    private DataSourceClient registerData;
    private Status subscribeData;
    HandlerThread threadRemoteListener;
    IncomingHandler incomingHandler;

    private Semaphore semaphoreReceive;
    public final ReentrantLock mutex = new ReentrantLock();

    private HashMap<Integer, OnReceiveListener> ds_idOnReceiveListenerHashMap = new HashMap<>();
    private Context context;
    private ServiceConnection connection;//receives callbacks from bind and unbind invocations
    private Messenger sendMessenger = null;
    private Messenger replyMessenger = null; //invocation replies are processed by this Messenger
    private OnConnectionListener onConnectionListener;
    private static final long WAIT_TIME = 3000;


    public DataKitAPIExecute(Context context) {
        this.context = context;
        isConnected = false;
        sessionId = -1;
        sendMessenger = null;
    }

    public boolean isConnected() {
        return sendMessenger != null && isConnected;
    }

    private void createThreadRemoteListener() {
        threadRemoteListener = new HandlerThread("MyHandlerThread");
        threadRemoteListener.start();
        incomingHandler = new IncomingHandler(threadRemoteListener.getLooper());
        this.replyMessenger = new Messenger(incomingHandler);
    }

    private void startRemoteService() throws DataKitNotFoundException {
        Intent intent = new Intent();
        intent.setClassName(Constants.PACKAGE_NAME, Constants.SERVICE_NAME);
        this.connection = new RemoteServiceConnection();
        intent.putExtra("name", context.getPackageName());
        intent.putExtra("messenger", this.replyMessenger);
        if (!context.bindService(intent, this.connection, Context.BIND_AUTO_CREATE)) {
            incomingHandler.removeCallbacks(threadRemoteListener);
            ds_idOnReceiveListenerHashMap.clear();
            threadRemoteListener.quit();
            context.unbindService(connection);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            isConnected = false;
            throw new DataKitNotFoundException(new Status(Status.ERROR_BOUND));
        }
    }


    protected void connect(OnConnectionListener onConnectionListener) throws DataKitException {
        try {
            mutex.lock();
            this.onConnectionListener = onConnectionListener;
            ds_idOnReceiveListenerHashMap.clear();
            sessionId = new Random().nextInt();
            semaphoreReceive = new Semaphore(0, true);
            createThreadRemoteListener();
            startRemoteService();
        } catch (Exception ignored) {
        } finally {
            mutex.unlock();
        }

    }

    public void disconnect() {
        try {

            mutex.lock();
            isConnected = false;
            sessionId = -1;
            ds_idOnReceiveListenerHashMap.clear();
            incomingHandler.removeCallbacks(threadRemoteListener);
            if (threadRemoteListener.isAlive())
                threadRemoteListener.quit();
            context.unbindService(connection);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                isConnected = false;
            }
        } catch (Exception e) {
            isConnected = false;
        } finally {
            mutex.unlock();
        }
    }


    private void prepareAndSend(Bundle bundle, int messageType) throws RemoteException {

        Message message = Message.obtain(null, 0, 0, 0);
        message.what = messageType;
        message.arg1 = sessionId;
        message.setData(bundle);
        message.replyTo = replyMessenger;
        sendMessenger.send(message);
    }


    public PendingResult<DataSourceClient> register(final DataSourceBuilder dataSourceBuilder) throws DataKitException {
        PendingResult<DataSourceClient> pendingResult = new PendingResult<DataSourceClient>() {
            @Override
            public DataSourceClient await() {
                try {
                    mutex.tryLock(WAIT_TIME, TimeUnit.MILLISECONDS);
                    DataSource dataSource = prepareDataSource(dataSourceBuilder);
                    Bundle bundle = new Bundle();
                    bundle.putParcelable(DataSource.class.getSimpleName(), dataSource);
                    prepareAndSend(bundle, MessageType.REGISTER);
                    semaphoreReceive.tryAcquire(WAIT_TIME, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    registerData = null;
                } finally {
                    if (mutex.isLocked())
                        mutex.unlock();
                }
                return registerData;
            }
        };
        return pendingResult;
    }

    public PendingResult<Status> unsubscribe(final int ds_id) throws DataKitException {
        PendingResult<Status> pendingResult = new PendingResult<Status>() {
            @Override
            public Status await() {
                try {
                    unsubscribeData = null;
                    mutex.tryLock(WAIT_TIME, TimeUnit.MILLISECONDS);
                    ds_idOnReceiveListenerHashMap.remove(ds_id);
                    Bundle bundle = new Bundle();
                    bundle.putInt(Constants.RC_DSID, ds_id);
                    prepareAndSend(bundle, MessageType.UNSUBSCRIBE);
                    semaphoreReceive.tryAcquire(WAIT_TIME, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    unsubscribeData = null;
                } finally {
                    if (mutex.isLocked())
                        mutex.unlock();
                }
                return unsubscribeData;
            }
        };
        return pendingResult;
    }

    public PendingResult<Status> unregister(final DataSourceClient dataSourceClient) throws DataKitException {
        PendingResult<Status> pendingResult = new PendingResult<Status>() {
            @Override
            public Status await() {
                try {
                    unregisterData = null;
                    mutex.tryLock(WAIT_TIME, TimeUnit.MILLISECONDS);
                    Bundle bundle = new Bundle();
                    bundle.putInt(Constants.RC_DSID, dataSourceClient.getDs_id());
                    prepareAndSend(bundle, MessageType.UNREGISTER);
                    semaphoreReceive.tryAcquire(WAIT_TIME, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    unregisterData = null;
                } finally {
                    if (mutex.isLocked())
                        mutex.unlock();
                }
                return unregisterData;
            }
        };
        return pendingResult;
    }

    public Status subscribe(final DataSourceClient dataSourceClient, OnReceiveListener onReceiveListener) throws DataKitException {
        try {
            subscribeData = null;
            mutex.tryLock(WAIT_TIME, TimeUnit.MILLISECONDS);
            ds_idOnReceiveListenerHashMap.put(dataSourceClient.getDs_id(), onReceiveListener);
            Bundle bundle = new Bundle();
            bundle.putInt(Constants.RC_DSID, dataSourceClient.getDs_id());
            prepareAndSend(bundle, MessageType.SUBSCRIBE);
            semaphoreReceive.tryAcquire(WAIT_TIME, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            subscribeData = null;
        } finally {
            if (mutex.isLocked())
                mutex.unlock();
        }
        return subscribeData;
    }


    public PendingResult<ArrayList<DataSourceClient>> find(final DataSourceBuilder dataSourceBuilder) throws DataKitException {
        PendingResult<ArrayList<DataSourceClient>> pendingResult = new PendingResult<ArrayList<DataSourceClient>>() {
            @Override
            public ArrayList<DataSourceClient> await() {
                try {
                    findData = null;
                    mutex.tryLock(WAIT_TIME, TimeUnit.MILLISECONDS);
                    final DataSource dataSource = dataSourceBuilder.build();
                    Bundle bundle = new Bundle();
                    bundle.putParcelable(DataSource.class.getSimpleName(), dataSource);
                    prepareAndSend(bundle, MessageType.FIND);
                    semaphoreReceive.tryAcquire(WAIT_TIME, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    findData = null;
                } finally {
                    if (mutex.isLocked())
                        mutex.unlock();
                }
                return findData;
            }
        };
        return pendingResult;
    }

    public PendingResult<ArrayList<DataType>> query(final DataSourceClient dataSourceClient, final long starttimestamp, final long endtimestamp) throws DataKitException {
        return new PendingResult<ArrayList<DataType>>() {
            @Override
            public ArrayList<DataType> await() {
                try {
                    queryData = null;
                    mutex.tryLock(WAIT_TIME, TimeUnit.MILLISECONDS);
                    Bundle bundle = new Bundle();
                    bundle.putInt(Constants.RC_DSID, dataSourceClient.getDs_id());
                    bundle.putLong(Constants.RC_STARTTIMESTAMP, starttimestamp);
                    bundle.putLong(Constants.RC_ENDTIMESTAMP, endtimestamp);
                    prepareAndSend(bundle, MessageType.QUERY);
                    semaphoreReceive.tryAcquire(WAIT_TIME, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    queryData = null;
                } finally {
                    if (mutex.isLocked())
                        mutex.unlock();
                }
                return queryData;
            }
        };
    }

    public PendingResult<ArrayList<DataType>> query(final DataSourceClient dataSourceClient, final int last_n_sample) throws DataKitException {

        return new PendingResult<ArrayList<DataType>>() {
            @Override
            public ArrayList<DataType> await() {
                try {
                    queryData = null;
                    mutex.tryLock(WAIT_TIME, TimeUnit.MILLISECONDS);
                    Bundle bundle = new Bundle();
                    bundle.putInt(Constants.RC_DSID, dataSourceClient.getDs_id());
                    bundle.putInt(Constants.RC_LAST_N_SAMPLE, last_n_sample);
                    prepareAndSend(bundle, MessageType.QUERY);
                    semaphoreReceive.tryAcquire(WAIT_TIME, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    queryData = null;
                } finally {
                    if (mutex.isLocked())
                        mutex.unlock();
                }
                return queryData;
            }
        };
    }

    public PendingResult<ArrayList<DataType>> queryHFlastN(final DataSourceClient dataSourceClient, final int last_n_sample) throws DataKitException {
        return new PendingResult<ArrayList<DataType>>() {
            @Override
            public ArrayList<DataType> await() {
                try {
                    queryHFLastNData = null;
                    mutex.tryLock(WAIT_TIME, TimeUnit.MILLISECONDS);
                    Bundle bundle = new Bundle();
                    bundle.putInt(Constants.RC_DSID, dataSourceClient.getDs_id());
                    bundle.putInt(Constants.RC_LAST_N_SAMPLE, last_n_sample);
                    prepareAndSend(bundle, MessageType.QUERYHFLASTN);
                    semaphoreReceive.tryAcquire(WAIT_TIME, TimeUnit.MILLISECONDS);

                } catch (Exception e) {
                    queryHFLastNData = null;
                    //                  semaphoreReceive.release();
                } finally {
                    if (mutex.isLocked())
                        mutex.unlock();
                }
                return queryHFLastNData;
            }
        };
    }

    public PendingResult<ArrayList<RowObject>> queryFromPrimaryKey(final DataSourceClient dataSourceClient, final long lastSyncedValue, final int limit) throws DataKitException {
        return new PendingResult<ArrayList<RowObject>>() {
            @Override
            public ArrayList<RowObject> await() {
                try {
                    queryPrimaryKeyData = null;
                    mutex.tryLock(WAIT_TIME, TimeUnit.MILLISECONDS);
                    Bundle bundle = new Bundle();
                    bundle.putInt(Constants.RC_DSID, dataSourceClient.getDs_id());
                    bundle.putLong(Constants.RC_LAST_KEY, lastSyncedValue);
                    bundle.putInt(Constants.RC_LIMIT, limit);
                    prepareAndSend(bundle, MessageType.QUERYPRIMARYKEY);
                    semaphoreReceive.tryAcquire(WAIT_TIME, TimeUnit.MILLISECONDS);

                } catch (Exception e) {
                    queryPrimaryKeyData = null;
                } finally {
                    if (mutex.isLocked())
                        mutex.unlock();
                }
                return queryPrimaryKeyData;
            }
        };
    }

    public PendingResult<ArrayList<RowObject>> queryHFFromPrimaryKey(final DataSourceClient dataSourceClient, final long lastSyncedValue, final int limit) throws DataKitException {
        return new PendingResult<ArrayList<RowObject>>() {
            @Override
            public ArrayList<RowObject> await() {
                try {
                    queryHFPrimaryKeyData = null;
                    mutex.tryLock(WAIT_TIME, TimeUnit.MILLISECONDS);
                    Bundle bundle = new Bundle();
                    bundle.putInt(Constants.RC_DSID, dataSourceClient.getDs_id());
                    bundle.putLong(Constants.RC_LAST_KEY, lastSyncedValue);
                    bundle.putInt(Constants.RC_LIMIT, limit);
                    prepareAndSend(bundle, MessageType.QUERYHFPRIMARYKEY);
                    semaphoreReceive.tryAcquire(WAIT_TIME, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    queryHFPrimaryKeyData = null;
                } finally {
                    if (mutex.isLocked())
                        mutex.unlock();
                }
                return queryHFPrimaryKeyData;
            }
        };
    }

    public PendingResult<DataTypeLong> querySize() throws DataKitException {
        return new PendingResult<DataTypeLong>() {
            @Override
            public DataTypeLong await() {
                try {
                    querySizeData = null;
                    mutex.tryLock(WAIT_TIME, TimeUnit.MILLISECONDS);
                    Bundle bundle = new Bundle();
                    prepareAndSend(bundle, MessageType.QUERYSIZE);
                    semaphoreReceive.tryAcquire(WAIT_TIME, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    querySizeData = null;
                } finally {
                    if (mutex.isLocked())
                        mutex.unlock();
                }
                return querySizeData;
            }
        };
    }


    public void insert(final DataSourceClient dataSourceClient, final DataType dataType) throws DataKitException {
        try {
            mutex.tryLock(WAIT_TIME, TimeUnit.MILLISECONDS);
            Bundle bundle = new Bundle();
            bundle.putParcelable(DataType.class.getSimpleName(), dataType);
            bundle.putInt(Constants.RC_DSID, dataSourceClient.getDs_id());
            prepareAndSend(bundle, MessageType.INSERT);
        } catch (Exception e) {
            throw new DataKitException(e.getCause());
        } finally {
            if (mutex.isLocked())
                mutex.unlock();
        }
    }

    public void insertHighFrequency(final DataSourceClient dataSourceClient, final DataTypeDoubleArray dataType) throws DataKitException {
        try {
            mutex.tryLock(WAIT_TIME, TimeUnit.MILLISECONDS);
            Bundle bundle = new Bundle();
            bundle.putParcelable(DataTypeDoubleArray.class.getSimpleName(), dataType);
            bundle.putInt(Constants.RC_DSID, dataSourceClient.getDs_id());
            prepareAndSend(bundle, MessageType.INSERT_HIGH_FREQUENCY);
        } catch (Exception e) {
            throw new DataKitException(e.getCause());
        } finally {
            if (mutex.isLocked())
                mutex.unlock();
        }
    }

    private DataSource prepareDataSource(DataSourceBuilder dataSourceBuilder) {
        String versionName = null;
        int versionNumber = 0;
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            versionName = pInfo.versionName;
            versionNumber = pInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        ApplicationBuilder applicationBuilder;
        if (dataSourceBuilder.build().getApplication() == null)
            applicationBuilder = new ApplicationBuilder();
        else
            applicationBuilder = new ApplicationBuilder(dataSourceBuilder.build().getApplication());
        Application application = applicationBuilder.setId(context.getPackageName()).setMetadata(METADATA.VERSION_NAME, versionName).setMetadata(METADATA.VERSION_NUMBER, String.valueOf(versionNumber)).build();
        dataSourceBuilder = dataSourceBuilder.setApplication(application);
        return dataSourceBuilder.build();
    }

    private class RemoteServiceConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName component, IBinder binder) {
            sendMessenger = new Messenger(binder);
            isConnected = true;
            onConnectionListener.onConnected();
        }

        @Override
        public void onServiceDisconnected(ComponentName component) {
            sendMessenger = null;
            isConnected = false;
        }
    }

    private class IncomingHandler extends Handler {
        IncomingHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            if (!isConnected()) {
                return;
            }
            msg.getData().setClassLoader(Status.class.getClassLoader());
            int curSessionId = msg.arg1;
            switch (msg.what) {
                case MessageType.INTERNAL_ERROR:
                    Log.e(TAG, "DataKitAPI Internal Error");
                    break;
                case MessageType.REGISTER:
                    msg.getData().setClassLoader(DataSourceClient.class.getClassLoader());
                    if (curSessionId != sessionId) registerData = null;
                    else
                        registerData = msg.getData().getParcelable(DataSourceClient.class.getSimpleName());
                    semaphoreReceive.release();
                    break;
                case MessageType.UNREGISTER:
                    msg.getData().setClassLoader(Status.class.getClassLoader());
                    if (curSessionId != sessionId) unregisterData = null;
                    else
                        unregisterData = msg.getData().getParcelable(Status.class.getSimpleName());
                    semaphoreReceive.release();
                    break;
                case MessageType.SUBSCRIBE:
                    msg.getData().setClassLoader(DataType.class.getClassLoader());
                    if (curSessionId != sessionId) subscribeData = null;
                    else
                        subscribeData = msg.getData().getParcelable(Status.class.getSimpleName());
                    semaphoreReceive.release();
                    break;
                case MessageType.UNSUBSCRIBE:
                    msg.getData().setClassLoader(Status.class.getClassLoader());
                    if (curSessionId != sessionId) unsubscribeData = null;
                    else
                        unsubscribeData = msg.getData().getParcelable(Status.class.getSimpleName());
                    semaphoreReceive.release();
                    break;
                case MessageType.FIND:
                    msg.getData().setClassLoader(DataSourceClient.class.getClassLoader());
                    if (curSessionId != sessionId) findData = null;
                    else
                        findData = msg.getData().getParcelableArrayList(DataSourceClient.class.getSimpleName());
                    semaphoreReceive.release();
                    break;
                case MessageType.INSERT:
                    break;
                case MessageType.INSERT_HIGH_FREQUENCY:
                    break;
                case MessageType.QUERY:
                    msg.getData().setClassLoader(DataType.class.getClassLoader());
                    if (curSessionId != sessionId) queryData = null;
                    else
                        queryData = msg.getData().getParcelableArrayList(DataType.class.getSimpleName());
                    semaphoreReceive.release();
                    break;
                case MessageType.QUERYHFLASTN:
                    msg.getData().setClassLoader(DataType.class.getClassLoader());
                    if (curSessionId != sessionId) queryHFLastNData = null;
                    else
                        queryHFLastNData = msg.getData().getParcelableArrayList(DataType.class.getSimpleName());
                    semaphoreReceive.release();
                    break;
                case MessageType.QUERYSIZE:
                    msg.getData().setClassLoader(DataType.class.getClassLoader());
                    if (curSessionId != sessionId) querySizeData = null;
                    else
                        querySizeData = msg.getData().getParcelable(DataTypeLong.class.getSimpleName());

                    semaphoreReceive.release();
                    break;
                case MessageType.QUERYPRIMARYKEY:
                    msg.getData().setClassLoader(RowObject.class.getClassLoader());
                    if (curSessionId != sessionId) queryPrimaryKeyData = null;
                    else
                        queryPrimaryKeyData = msg.getData().getParcelableArrayList(RowObject.class.getSimpleName());
                    semaphoreReceive.release();
                    break;
                case MessageType.QUERYHFPRIMARYKEY:
                    msg.getData().setClassLoader(RowObject.class.getClassLoader());
                    if (curSessionId != sessionId) queryHFPrimaryKeyData = null;
                    else
                        queryHFPrimaryKeyData = msg.getData().getParcelableArrayList(RowObject.class.getSimpleName());
                    semaphoreReceive.release();
                    break;
                case MessageType.SUBSCRIBED_DATA:
                    try {
                        if (sessionId == -1) throw new Exception("abc");
                        msg.getData().setClassLoader(DataType.class.getClassLoader());
                        subscribedData = msg.getData().getParcelable(DataType.class.getSimpleName());
                        int ds_id = msg.getData().getInt(Constants.RC_DSID, -1);
                        if (!ds_idOnReceiveListenerHashMap.containsKey(ds_id))
                            throw new Exception("abc");
                        ds_idOnReceiveListenerHashMap.get(ds_id).onReceived(subscribedData);
                    } catch (Exception e) {
                        disconnect();
                    }
                    break;
            }
        }
    }
}
