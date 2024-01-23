// Copyright 2020 Espressif Systems (Shanghai) PTE LTD
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.espressif;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.espressif.AppConstants.Companion.UpdateEventType;
import com.espressif.cloudapi.ApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.db.EspDatabase;
import com.espressif.local_control.EspLocalDevice;
import com.espressif.local_control.LocalControlApiManager;
import com.espressif.local_control.mDNSManager;
import com.espressif.matter.ChipClient;
import com.espressif.matter.ClustersHelper;
import com.espressif.matter.DeviceMatterInfo;
import com.espressif.matter.LevelControlClusterHelper;
import com.espressif.matter.MatterFabricUtils;
import com.espressif.provisioning.ESPProvisionManager;
import com.espressif.rainmaker.BuildConfig;
import com.espressif.rainmaker.R;
import com.espressif.ui.activities.ConsentActivity;
import com.espressif.ui.models.Automation;
import com.espressif.ui.models.Device;
import com.espressif.ui.models.EspNode;
import com.espressif.ui.models.EspOtaUpdate;
import com.espressif.ui.models.Group;
import com.espressif.ui.models.Param;
import com.espressif.ui.models.Scene;
import com.espressif.ui.models.Schedule;
import com.espressif.ui.models.Service;
import com.espressif.ui.models.UpdateEvent;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.greenrobot.eventbus.EventBus;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import javax.security.auth.x500.X500Principal;

import chip.devicecontroller.ChipClusters;
import dagger.hilt.android.HiltAndroidApp;

@HiltAndroidApp
public class EspApplication extends Application {

    private static final String TAG = EspApplication.class.getSimpleName();

    public static String BASE_URL = BuildConfig.BASE_URL;

    private AppState appState = AppState.NO_USER_LOGIN;

    public HashMap<String, EspNode> nodeMap;
    public HashMap<String, Schedule> scheduleMap;
    public HashMap<String, Scene> sceneMap;
    public HashMap<String, EspLocalDevice> localDeviceMap;
    public HashMap<String, Group> groupMap;
    public HashMap<String, Automation> automations;

    public HashMap<String, String> matterRmNodeIdMap;
    public HashMap<String, ChipClient> chipClientMap;
    public HashMap<String, List<DeviceMatterInfo>> matterDeviceInfoMap;
    public ArrayList<String> availableMatterDevices;
    public EspOtaUpdate otaUpdateInfo;

    private SharedPreferences appPreferences;
    private ApiManager apiManager;
    private mDNSManager mdnsManager;
    private String deviceToken;
    private KeyStore keyStore = null;

    public String mGroupId, mFabricId, mRootCa, mIpk;

    public enum AppState {
        NO_USER_LOGIN,
        GETTING_DATA,
        GET_DATA_SUCCESS,
        GET_DATA_FAILED,
        NO_INTERNET,
        REFRESH_DATA
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "ESP Application is created");
        nodeMap = new HashMap<>();
        scheduleMap = new HashMap<>();
        sceneMap = new HashMap<>();
        localDeviceMap = new HashMap<>();
        groupMap = new HashMap<>();
        automations = new HashMap<>();
        matterRmNodeIdMap = new HashMap<>();
        chipClientMap = new HashMap<>();
        matterDeviceInfoMap = new HashMap<>();
        availableMatterDevices = new ArrayList<>();

        appPreferences = getSharedPreferences(AppConstants.ESP_PREFERENCES, Context.MODE_PRIVATE);
        BASE_URL = appPreferences.getString(AppConstants.KEY_BASE_URL, BuildConfig.BASE_URL);
        apiManager = ApiManager.getInstance(this);
        ESPProvisionManager.getInstance(this);
        if (BuildConfig.isLocalControlSupported) {
            mdnsManager = mDNSManager.getInstance(getApplicationContext(), AppConstants.MDNS_SERVICE_TYPE, listener);
        }

        if (isPlayServicesAvailable()) {
            FirebaseMessaging.getInstance().setAutoInitEnabled(false);
            setupNotificationChannels();
        }
    }

    public AppState getAppState() {
        return appState;
    }

    public void changeAppState(AppState newState, Bundle extras) {

        switch (newState) {
            case GETTING_DATA:
                if (BuildConfig.isLocalControlSupported) {
                    mdnsManager.initializeNsd();
                }
            case REFRESH_DATA:
                if (!appState.equals(newState)) {
                    appState = newState;
                    getNodesFromCloud();
                }
                EventBus.getDefault().post(new UpdateEvent(UpdateEventType.EVENT_STATE_CHANGE_UPDATE));
                break;

            case GET_DATA_FAILED:
                appState = newState;
                UpdateEvent updateEvent = new UpdateEvent(UpdateEventType.EVENT_STATE_CHANGE_UPDATE);
                if (extras != null) {
                    updateEvent.setData(extras);
                }
                EventBus.getDefault().post(updateEvent);
                startLocalDeviceDiscovery();
                break;

            case NO_USER_LOGIN:
                Intent loginActivity = new Intent(this, ConsentActivity.class);
                loginActivity.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
                        | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(loginActivity);
                appState = newState;
                EventBus.getDefault().post(new UpdateEvent(UpdateEventType.EVENT_STATE_CHANGE_UPDATE));
                break;

            case GET_DATA_SUCCESS:
            case NO_INTERNET:
                appState = newState;
                EventBus.getDefault().post(new UpdateEvent(UpdateEventType.EVENT_STATE_CHANGE_UPDATE));
                startLocalDeviceDiscovery();
                for (Map.Entry<String, String> entry : matterRmNodeIdMap.entrySet()) {
                    String matterNodeId = entry.getValue();
                    initChipController(matterNodeId);
                }
                break;
        }
    }

    private void getNodesFromCloud() {

        apiManager.getNodes(new ApiResponseListener() {

            @Override
            public void onSuccess(Bundle data) {

                if (BuildConfig.isNodeGroupingSupported) {
                    getGroups();
                } else {
                    if (BuildConfig.isMatterSupported) {
                        getFabricDetails();
                    } else {
                        changeAppState(AppState.GET_DATA_SUCCESS, null);
                    }
                }
            }

            @Override
            public void onResponseFailure(Exception exception) {
                Bundle data = new Bundle();
                data.putString(AppConstants.KEY_ERROR_MSG, exception.getMessage());
                changeAppState(EspApplication.AppState.GET_DATA_FAILED, data);
            }

            @Override
            public void onNetworkFailure(Exception exception) {
                changeAppState(AppState.NO_INTERNET, null);
            }
        });
    }

    private void getGroups() {

        apiManager.getUserGroups(null, new ApiResponseListener() {

            @Override
            public void onSuccess(Bundle data) {
                if (BuildConfig.isMatterSupported) {
                    getFabricDetails();
                } else {
                    changeAppState(AppState.GET_DATA_SUCCESS, null);
                }
            }

            @Override
            public void onResponseFailure(Exception exception) {
                Bundle data = new Bundle();
                data.putString(AppConstants.KEY_ERROR_MSG, exception.getMessage());
                changeAppState(EspApplication.AppState.GET_DATA_FAILED, data);
            }

            @Override
            public void onNetworkFailure(Exception exception) {
                changeAppState(AppState.NO_INTERNET, null);
            }
        });
    }

    private void getFabricDetails() {

        HashMap<String, Group> fabricMap = new HashMap<>();
        for (Map.Entry<String, Group> entry : groupMap.entrySet()) {

            if (entry.getValue().isMatter()) {
                fabricMap.put(entry.getKey(), entry.getValue());
            }
        }

        apiManager.getMatterNodeIds(fabricMap, new ApiResponseListener() {

            @Override
            public void onSuccess(Bundle data) {
                getUserNOCs();
            }

            @Override
            public void onResponseFailure(Exception exception) {
                getUserNOCs();
            }

            @Override
            public void onNetworkFailure(Exception exception) {
                getUserNOCs();
            }
        });
    }

    public void createHomeFabric(ApiResponseListener listener) {

        JsonObject body = new JsonObject();
        body.addProperty(AppConstants.KEY_GROUP_NAME, "Home");
        body.addProperty(AppConstants.KEY_IS_MATTER, true);

        apiManager.createGroup(body, new ApiResponseListener() {
            @Override
            public void onSuccess(@Nullable Bundle data) {
                getUserNOCForHomeGroup(listener);
            }

            @Override
            public void onResponseFailure(@NonNull Exception exception) {
                listener.onResponseFailure(exception);
            }

            @Override
            public void onNetworkFailure(@NonNull Exception exception) {
                listener.onNetworkFailure(exception);
            }
        });
    }

    private void getUserNOCForHomeGroup(ApiResponseListener listener) {

        try {
            keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException |
                 IOException e) {
            e.printStackTrace();
        }

        HashMap<String, Group> fabricMap = new HashMap<>();
        for (Map.Entry<String, Group> entry : groupMap.entrySet()) {

            if (entry.getValue().isMatter()) {
                fabricMap.put(entry.getKey(), entry.getValue());
            }
        }

        HashMap<String, JsonObject> requestMap = new HashMap<>();
        HashMap<String, KeyPair> keyPairHashMap = new HashMap<>();
        for (Map.Entry<String, Group> entry : fabricMap.entrySet()) {

            String groupId = entry.getKey();
            String fabricId = entry.getValue().getFabricId();

            JsonObject body = new JsonObject();
            body.addProperty(AppConstants.KEY_OPERATION, AppConstants.KEY_OPERATION_ADD);
            body.addProperty(AppConstants.KEY_CSR_TYPE, "user");

            JsonObject csrJson = new JsonObject();
            csrJson.addProperty(AppConstants.KEY_GROUP_ID, groupId);

            KeyPair keyPair = MatterFabricUtils.Companion.generateKeypair(fabricId);
            PKCS10CertificationRequestBuilder p10Builder =
                    new JcaPKCS10CertificationRequestBuilder(new X500Principal(""), keyPair.getPublic());
            JcaContentSignerBuilder csBuilder = new JcaContentSignerBuilder("SHA256withECDSA");
            ContentSigner signer = null;
            String csrContent = null;

            try {
                signer = csBuilder.build(keyPair.getPrivate());
            } catch (OperatorCreationException e) {
                throw new RuntimeException(e);
            }

            PKCS10CertificationRequest csr = p10Builder.build(signer);

            try {
                csrContent = Base64.getEncoder().encodeToString(csr.getEncoded());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            String finalCsr = AppConstants.CERT_BEGIN + "\n" + csrContent + "\n" + AppConstants.CERT_END;
            csrJson.addProperty(AppConstants.KEY_CSR, finalCsr);

            keyPairHashMap.put(fabricId, keyPair);

            JsonArray csrArr = new JsonArray();
            csrArr.add(csrJson);
            body.add(AppConstants.KEY_CSR_REQUESTS, csrArr);
            requestMap.put(groupId, body);
        }

        apiManager.getAllUserNOCs(requestMap, new ApiResponseListener() {
            @Override
            public void onSuccess(Bundle data) {

                for (Map.Entry<String, Group> entry : groupMap.entrySet()) {
                    if (entry.getValue().isMatter()) {
                        Group g = entry.getValue();
                        if (g.getFabricDetails() != null) {

                            String userNoc = g.getFabricDetails().getUserNoc();
                            String rootCa = g.getFabricDetails().getRootCa();
                            String fabricId = g.getFabricId();
                            Certificate[] certificates = new Certificate[2];
                            certificates[0] = MatterFabricUtils.Companion.decode(userNoc);
                            certificates[1] = MatterFabricUtils.Companion.decode(rootCa);

                            try {
                                keyStore.setKeyEntry(fabricId, keyPairHashMap.get(fabricId).getPrivate(), null, certificates);
                            } catch (KeyStoreException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                }
                initChipControllerForHomeGroup();
                listener.onSuccess(null);
            }

            @Override
            public void onResponseFailure(Exception exception) {
                initChipControllerForHomeGroup();
                listener.onResponseFailure(exception);
            }

            @Override
            public void onNetworkFailure(Exception exception) {
                initChipControllerForHomeGroup();
                listener.onNetworkFailure(exception);
            }
        });
    }

    private void getUserNOCs() {

        Log.d(TAG, "Get Use NOCs ...............................");

        try {
            keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException |
                 IOException e) {
            e.printStackTrace();
        }

        HashMap<String, Group> fabricMap = new HashMap<>();
        for (Map.Entry<String, Group> entry : groupMap.entrySet()) {

            if (entry.getValue().isMatter()) {
                fabricMap.put(entry.getKey(), entry.getValue());
            }
        }

        HashMap<String, JsonObject> requestMap = new HashMap<>();
        HashMap<String, KeyPair> keyPairHashMap = new HashMap<>();
        for (Map.Entry<String, Group> entry : fabricMap.entrySet()) {

            String groupId = entry.getKey();
            String fabricId = entry.getValue().getFabricId();

            JsonObject body = new JsonObject();
            body.addProperty(AppConstants.KEY_OPERATION, AppConstants.KEY_OPERATION_ADD);
            body.addProperty(AppConstants.KEY_CSR_TYPE, "user");

            JsonObject csrJson = new JsonObject();
            csrJson.addProperty(AppConstants.KEY_GROUP_ID, groupId);

            // TODO Improvement - avoid every time csr creation if possible
            KeyPair keyPair = MatterFabricUtils.Companion.generateKeypair(fabricId);
            PKCS10CertificationRequestBuilder p10Builder =
                    new JcaPKCS10CertificationRequestBuilder(new X500Principal(""), keyPair.getPublic());
            JcaContentSignerBuilder csBuilder = new JcaContentSignerBuilder("SHA256withECDSA");
            ContentSigner signer = null;
            String csrContent = null;

            try {
                signer = csBuilder.build(keyPair.getPrivate());
            } catch (OperatorCreationException e) {
                throw new RuntimeException(e);
            }

            PKCS10CertificationRequest csr = p10Builder.build(signer);

            try {
                csrContent = Base64.getEncoder().encodeToString(csr.getEncoded());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            String finalCsr = AppConstants.CERT_BEGIN + "\n" + csrContent + "\n" + AppConstants.CERT_END;
            csrJson.addProperty(AppConstants.KEY_CSR, finalCsr);

            keyPairHashMap.put(fabricId, keyPair);

            JsonArray csrArr = new JsonArray();
            csrArr.add(csrJson);
            body.add(AppConstants.KEY_CSR_REQUESTS, csrArr);
            requestMap.put(groupId, body);
        }

        apiManager.getAllUserNOCs(requestMap, new ApiResponseListener() {
            @Override
            public void onSuccess(Bundle data) {
                for (Map.Entry<String, Group> entry : groupMap.entrySet()) {
                    if (entry.getValue().isMatter()) {
                        Group g = entry.getValue();
                        if (g.getFabricDetails() != null) {

                            String userNoc = g.getFabricDetails().getUserNoc();
                            String rootCa = g.getFabricDetails().getRootCa();

                            String fabricId = g.getFabricId();
                            Certificate[] certificates = new Certificate[2];
                            certificates[0] = MatterFabricUtils.Companion.decode(userNoc);
                            certificates[1] = MatterFabricUtils.Companion.decode(rootCa);

                            try {
                                keyStore.setKeyEntry(fabricId, keyPairHashMap.get(fabricId).getPrivate(), null, certificates);
                            } catch (KeyStoreException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                }
                changeAppState(AppState.GET_DATA_SUCCESS, null);
            }

            @Override
            public void onResponseFailure(Exception exception) {
                changeAppState(AppState.GET_DATA_SUCCESS, null);
            }

            @Override
            public void onNetworkFailure(Exception exception) {
                changeAppState(AppState.GET_DATA_SUCCESS, null);
            }
        });
    }

    private void initChipController(String matterNodeId) {
        Log.d(TAG, "Init ChipController for matter node id : " + matterNodeId);
        if (TextUtils.isEmpty(matterNodeId)) {
            Log.e(TAG, "======= Init ChipController will not be done. Matter node id is not available");
            return;
        }

        for (Map.Entry<String, Group> entry : groupMap.entrySet()) {

            if (entry.getValue().isMatter()) {
                Group g = entry.getValue();
                HashMap<String, String> nodeDetails = g.getNodeDetails();
                if (nodeDetails != null) {
                    for (Map.Entry<String, String> detail : nodeDetails.entrySet()) {

                        String nodeId = detail.getKey();
                        String mNodeId = detail.getValue();
                        String fabricId = "";
                        String ipk = "";
                        String rootCa = "";

                        if (!matterNodeId.equals(mNodeId)) {
                            continue;
                        }

                        Log.d(TAG, "Node detail, node id : " + nodeId + " and matter node id : " + matterNodeId);

                        if (g.getFabricDetails() != null) {
                            fabricId = g.getFabricDetails().getFabricId();
                            rootCa = g.getFabricDetails().getRootCa();
                            ipk = g.getFabricDetails().getIpk();

                            if (!chipClientMap.containsKey(matterNodeId)) {
                                if (!TextUtils.isEmpty(fabricId) && !TextUtils.isEmpty(rootCa)
                                        && !TextUtils.isEmpty(ipk) && !TextUtils.isEmpty(matterNodeId) && !TextUtils.isEmpty(matterNodeId)) {
                                    ChipClient chipClient = new ChipClient(this, g.getGroupId()
                                            , fabricId, rootCa, ipk);
                                    chipClientMap.put(matterNodeId, chipClient);
                                }
                            }
                            fetchDeviceMatterInfo(matterNodeId, nodeId);

                            EspNode node = nodeMap.get(nodeId);
                            if (node != null) {
                                String nodeType = node.getNewNodeType();
                                if (!TextUtils.isEmpty(nodeType) && nodeType.equals(AppConstants.NODE_TYPE_PURE_MATTER)) {
                                    addParamsForMatterOnlyDevice(nodeId, matterNodeId, node);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void initChipControllerForHomeGroup() {

        Log.d(TAG, "============================= init ChipController for home group");

        for (Map.Entry<String, Group> entry : groupMap.entrySet()) {

            if (entry.getValue().isMatter()) {
                Group g = entry.getValue();
                HashMap<String, String> nodeDetails = g.getNodeDetails();
                if (nodeDetails != null) {
                    for (Map.Entry<String, String> detail : nodeDetails.entrySet()) {

                        String nodeId = detail.getKey();
                        String matterNodeId = detail.getValue();
                        String fabricId = "";
                        String ipk = "";
                        String rootCa = "";

                        if (g.getFabricDetails() != null) {
                            fabricId = g.getFabricDetails().getFabricId();
                            rootCa = g.getFabricDetails().getRootCa();
                            ipk = g.getFabricDetails().getIpk();

                            if (TextUtils.isEmpty(matterNodeId)) {
                                return;
                            }

                            if (!chipClientMap.containsKey(matterNodeId)) {
                                if (!TextUtils.isEmpty(fabricId) && !TextUtils.isEmpty(rootCa)
                                        && !TextUtils.isEmpty(ipk) && !TextUtils.isEmpty(matterNodeId) && !TextUtils.isEmpty(matterNodeId)) {
                                    ChipClient chipClient = new ChipClient(this, g.getGroupId()
                                            , fabricId, rootCa, ipk);
                                    Log.d(TAG, "In it chip controller for matterNodeId id : " + matterNodeId);
                                    chipClientMap.put(matterNodeId, chipClient);
                                }
                            }

                            EspNode node = nodeMap.get(nodeId);
                            if (node != null) {
                                String nodeType = node.getNewNodeType();
                                if (!TextUtils.isEmpty(nodeType) && nodeType.equals(AppConstants.NODE_TYPE_PURE_MATTER)) {
                                    addParamsForMatterOnlyDevice(nodeId, matterNodeId, node);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public void fetchDeviceMatterInfo(String matterNodeId, String nodeId) {

        Log.d(TAG, "Fetching matter node info for matter node id : " + matterNodeId);
        BigInteger id = new BigInteger(matterNodeId, 16);
        long deviceId = id.longValue();
        Log.d(TAG, "Device id : " + deviceId);
        ClustersHelper clustersHelper = new ClustersHelper(chipClientMap.get(matterNodeId));
        CompletableFuture<List<DeviceMatterInfo>> result = clustersHelper.fetchDeviceMatterInfoAsync(deviceId);

        try {
            List<DeviceMatterInfo> matterDeviceInfo = result.get();
            Log.d(TAG, "Matter device information , Result : " + matterDeviceInfo);

            if (matterDeviceInfo != null && matterDeviceInfo.size() > 0) {
                for (DeviceMatterInfo info : matterDeviceInfo) {
                    Log.d(TAG, "Endpoint : " + info.getEndpoint());
                    Log.d(TAG, "Server Clusters : " + info.getServerClusters());
                    Log.d(TAG, "Client Clusters : " + info.getClientClusters());
                    Log.d(TAG, "Types : " + info.getTypes());
                }
                matterDeviceInfoMap.put(matterNodeId, matterDeviceInfo);
                nodeMap.get(nodeId).setOnline(true);
                availableMatterDevices.add(matterNodeId);
                EventBus.getDefault().post(new UpdateEvent(UpdateEventType.EVENT_DEVICE_ONLINE));
            } else {
                matterDeviceInfoMap.remove(matterNodeId);
                availableMatterDevices.remove(matterNodeId);
                chipClientMap.remove(matterNodeId);
                if (nodeMap.containsKey(nodeId)) {
                    nodeMap.get(nodeId).setOnline(false);
                }
                EventBus.getDefault().post(new UpdateEvent(UpdateEventType.EVENT_DEVICE_OFFLINE));
            }
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void addParamsForMatterOnlyDevice(String nodeId, String matterNodeId, EspNode node) {

        BigInteger id = new BigInteger(matterNodeId, 16);
        long deviceId = id.longValue();
        Log.d(TAG, "Device id : " + deviceId);

        if (matterDeviceInfoMap.containsKey(matterNodeId)) {

            List<DeviceMatterInfo> matterDeviceInfo = matterDeviceInfoMap.get(matterNodeId);

            for (DeviceMatterInfo info : matterDeviceInfo) {
                Log.d(TAG, "Endpoint : " + info.getEndpoint());
                Log.d(TAG, "Server Clusters : " + info.getServerClusters());
                Log.d(TAG, "Client Clusters : " + info.getClientClusters());
                Log.d(TAG, "Types : " + info.getTypes());

                if (info.getEndpoint() == AppConstants.ENDPOINT_1) {
                    String deviceType = "";
                    List<Object> serverClusters = info.getServerClusters();
                    List<Object> clientClusters = info.getClientClusters();
                    ArrayList<Device> devices = node.getDevices();

                    if (devices == null || devices.size() == 0) {
                        Device device = new Device(nodeId);
                        devices = new ArrayList<>();
                        devices.add(device);
                        node.setDevices(devices);
                    }

                    ArrayList<String> properties = new ArrayList<>();
                    properties.add(AppConstants.KEY_PROPERTY_WRITE);
                    properties.add(AppConstants.KEY_PROPERTY_READ);

                    for (Object cluster : serverClusters) {

                        long clusterId = (long) cluster;

                        if (clusterId == ChipClusters.OnOffCluster.CLUSTER_ID) {

                            Log.d(TAG, "Found On Off Cluster in server clusters");
                            Device device = devices.get(0);
                            deviceType = AppConstants.ESP_DEVICE_LIGHT;
                            device.setDeviceType(deviceType);
                            ArrayList<Param> params = device.getParams();
                            if (params == null || params.size() == 0) {
                                params = new ArrayList<>();
                            }
                            boolean isParamAvailable = isParamAvailableInList(params, AppConstants.PARAM_TYPE_POWER);

                            if (!isParamAvailable) {
                                // Add on/off param
                                addToggleParam(params, properties);
                            }
                            device.setParams(params);

                        } else if (clusterId == ChipClusters.LevelControlCluster.CLUSTER_ID) {

                            Log.d(TAG, "Found level control Cluster in server clusters");
                            Device device = devices.get(0);
                            ArrayList<Param> params = device.getParams();
                            if (params == null || params.size() == 0) {
                                params = new ArrayList<>();
                            }
                            boolean isParamAvailable = isParamAvailableInList(params, AppConstants.PARAM_TYPE_BRIGHTNESS);
                            Param brightnessParam = null;
                            if (isParamAvailable) {
                                for (Param p : params) {
                                    if (p.getParamType().equals(AppConstants.PARAM_TYPE_BRIGHTNESS)) {
                                        brightnessParam = p;
                                        break;
                                    }
                                }
                            }

                            if (!isParamAvailable) {
                                // Add brightness param
                                brightnessParam = new Param();
                                brightnessParam.setDynamicParam(true);
                                brightnessParam.setDataType("int");
                                brightnessParam.setUiType(AppConstants.UI_TYPE_SLIDER);
                                brightnessParam.setParamType(AppConstants.PARAM_TYPE_BRIGHTNESS);
                                brightnessParam.setName("Brightness");
                                brightnessParam.setMinBounds(0);
                                brightnessParam.setMaxBounds(100);
                                brightnessParam.setProperties(properties);
                                params.add(brightnessParam);
                            }
                            device.setParams(params);

                            LevelControlClusterHelper levelControlCluster = new LevelControlClusterHelper(chipClientMap.get(matterNodeId));
                            CompletableFuture<Integer> value = levelControlCluster.getCurrentLevelValueAsync(deviceId, AppConstants.ENDPOINT_1);
                            try {
                                Log.d(TAG, "Is done : " + value.isDone());
                                int level = value.get();
                                Log.d(TAG, "Received Brightness current value : " + level);
                                brightnessParam.setValue(level);
                            } catch (ExecutionException e) {
                                e.printStackTrace();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }

                        } else if (clusterId == ChipClusters.ColorControlCluster.CLUSTER_ID) {

                            Log.d(TAG, "Found color control Cluster in server clusters");
                            Device device = devices.get(0);
                            ArrayList<Param> params = device.getParams();
                            if (params == null || params.size() == 0) {
                                params = new ArrayList<>();
                            }
                            boolean isSatParamAvailable = isParamAvailableInList(params, AppConstants.PARAM_TYPE_SATURATION);
                            boolean isHueParamAvailable = isParamAvailableInList(params, AppConstants.PARAM_TYPE_HUE);

                            if (!isSatParamAvailable) {
                                // Add saturation param
                                Param saturation = new Param();
                                saturation.setDynamicParam(true);
                                saturation.setDataType("int");
                                saturation.setUiType(AppConstants.UI_TYPE_SLIDER);
                                saturation.setParamType(AppConstants.PARAM_TYPE_SATURATION);
                                saturation.setName("Saturation");
                                saturation.setProperties(properties);
                                saturation.setMinBounds(0);
                                saturation.setMaxBounds(100);
                                params.add(saturation);
                            }

                            if (!isHueParamAvailable) {
                                // Add hue param
                                Param hue = new Param();
                                hue.setDynamicParam(true);
                                hue.setDataType("int");
                                hue.setUiType(AppConstants.UI_TYPE_HUE_SLIDER);
                                hue.setParamType(AppConstants.PARAM_TYPE_HUE);
                                hue.setName("Hue");
                                hue.setProperties(properties);
                                params.add(hue);
                            }
                            device.setParams(params);
                        }
                    }
                    nodeMap.put(nodeId, node);

                    if (TextUtils.isEmpty(deviceType)) {

                        for (Object cluster : clientClusters) {
                            long clusterId = (long) cluster;

                            if (clusterId == ChipClusters.OnOffCluster.CLUSTER_ID) {

                                Log.d(TAG, "Found On Off Cluster in client clusters");

                                if (devices == null || devices.size() == 0) {
                                    Device device = new Device(nodeId);
                                    devices = new ArrayList<>();
                                    devices.add(device);
                                    node.setDevices(devices);
                                }

                                Device device = devices.get(0);
                                deviceType = AppConstants.ESP_DEVICE_SWITCH;
                                device.setDeviceType(deviceType);
                                ArrayList<Param> params = device.getParams();
                                if (params == null || params.size() == 0) {
                                    params = new ArrayList<>();
                                }
                                boolean isParamAvailable = isParamAvailableInList(params, AppConstants.PARAM_TYPE_POWER);

                                if (!isParamAvailable) {
                                    // Add on/off param
                                    addToggleParam(params, properties);
                                }
                                device.setParams(params);
                            }
                        }
                        nodeMap.put(nodeId, node);
                    }
                }
            }
        }
    }

    public void refreshData() {
        if (!appState.equals(AppState.GETTING_DATA)) {
            changeAppState(AppState.REFRESH_DATA, null);
        }
    }

    public void loginSuccess() {
        clearData();
    }

    public void registerDeviceToken() {

        if (!isPlayServicesAvailable()) {
            Log.e(TAG, "Google Play Services not available.");
            return;
        }
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(new OnCompleteListener<String>() {

            @Override
            public void onComplete(@NonNull Task<String> task) {
                if (!task.isSuccessful()) {
                    Log.d(TAG, "Fetching FCM registration token failed", task.getException());
                    return;
                }

                // Get new FCM registration token
                deviceToken = task.getResult();

                // Log and toast
                Log.e("FCM TOKEN  ", deviceToken);

                if (!TextUtils.isEmpty(deviceToken)) {
                    apiManager.registerDeviceToken(deviceToken, new ApiResponseListener() {
                        @Override
                        public void onSuccess(Bundle data) {
                        }

                        @Override
                        public void onResponseFailure(Exception exception) {
                        }

                        @Override
                        public void onNetworkFailure(Exception exception) {
                        }
                    });
                }
            }
        });
    }

    public void logout() {

        if (appState.equals(AppState.NO_USER_LOGIN)) {
            return;
        }

        // Do logout and clear all data
        if (!ApiManager.isOAuthLogin) {

            apiManager.logout(new ApiResponseListener() {

                @Override
                public void onSuccess(Bundle data) {
                    unregisterDeviceToken();
                }

                @Override
                public void onResponseFailure(Exception exception) {
                    // Ignore failure
                    unregisterDeviceToken();
                }

                @Override
                public void onNetworkFailure(Exception exception) {
                    // Ignore failure
                    unregisterDeviceToken();
                }
            });
        } else {
            unregisterDeviceToken();
        }
        clearUserSession();
    }

    private void unregisterDeviceToken() {
        if (isPlayServicesAvailable()) {
            // Delete endpoint API
            apiManager.unregisterDeviceToken(deviceToken, new ApiResponseListener() {
                @Override
                public void onSuccess(Bundle data) {
                }

                @Override
                public void onResponseFailure(Exception exception) {
                }

                @Override
                public void onNetworkFailure(Exception exception) {
                }
            });
        }
    }

    public void clearUserSession() {

        clearData();
        SharedPreferences.Editor editor = appPreferences.edit();
        editor.clear();
        editor.apply();

        SharedPreferences wifiNetworkPref = getSharedPreferences(AppConstants.PREF_FILE_WIFI_NETWORKS, Context.MODE_PRIVATE);
        SharedPreferences.Editor wifiNetworkEditor = wifiNetworkPref.edit();
        wifiNetworkEditor.clear();
        wifiNetworkEditor.apply();

        if (isPlayServicesAvailable()) {
            FirebaseMessaging.getInstance().deleteToken();
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.cancelAll();
        }

        Log.e(TAG, "Deleted all things from local storage.");
        changeAppState(AppState.NO_USER_LOGIN, null);
    }

    private void clearData() {
        EspDatabase.getInstance(this).getNodeDao().deleteAll();
        EspDatabase.getInstance(this).getGroupDao().deleteAll();
        EspDatabase.getInstance(this).getNotificationDao().deleteAll();
        nodeMap.clear();
        scheduleMap.clear();
        sceneMap.clear();
        localDeviceMap.clear();
        groupMap.clear();
        automations.clear();
    }

    public void startLocalDeviceDiscovery() {
        if (BuildConfig.isLocalControlSupported) {
            if (nodeMap.size() > 0) {
                mdnsManager.discoverServices();
            }
        }
    }

    public void stopLocalDeviceDiscovery() {
        if (BuildConfig.isLocalControlSupported) {
            mdnsManager.stopDiscovery();
        }
    }

    /**
     * This method is used to get copy of all devices.
     *
     * @return Copy of all devices array for the user.
     */
    public ArrayList<Device> getAllDevices() {
        ArrayList<Device> devices = new ArrayList<>();
        for (Map.Entry<String, EspNode> entry : nodeMap.entrySet()) {

            EspNode node = entry.getValue();
            if (node != null) {
                ArrayList<Device> espDevices = node.getDevices();
                Iterator<Device> iterator = espDevices.iterator();
                while (iterator.hasNext()) {
                    devices.add(new Device(iterator.next()));
                }
            }
        }
        return devices;
    }

    mDNSManager.mDNSEvenListener listener = new mDNSManager.mDNSEvenListener() {

        @Override
        public void deviceFound(EspLocalDevice newDevice) {

            Log.e(TAG, "Device Found on Local Network");
            final LocalControlApiManager localControlApiManager = new LocalControlApiManager(getApplicationContext());
            final String nodeId = newDevice.getNodeId();
            EspNode node = nodeMap.get(nodeId);
            Service localService = null;

            if (node == null) {
                Log.e(TAG, "Node is not available with id : " + nodeId);
                return;
            } else {
                ArrayList<Service> services = node.getServices();

                for (int i = 0; i < services.size(); i++) {
                    Service s = services.get(i);
                    if (!TextUtils.isEmpty(s.getType()) && s.getType().equals(AppConstants.SERVICE_TYPE_LOCAL_CONTROL)) {
                        localService = s;
                        break;
                    }
                }
            }

            Log.d(TAG, "Found node " + nodeId + " on local network.");
            if (localDeviceMap.containsKey(nodeId)) {
                Log.e(TAG, "Local Device session is already available");
                newDevice = localDeviceMap.get(nodeId);
            } else {
                localDeviceMap.put(nodeId, newDevice);
            }

            if (localService != null) {
                ArrayList<Param> popParams = localService.getParams();
                if (popParams != null) {
                    for (int paramIdx = 0; paramIdx < popParams.size(); paramIdx++) {
                        Param popParam = popParams.get(paramIdx);
                        if (AppConstants.PARAM_TYPE_LOCAL_CONTROL_POP.equalsIgnoreCase(popParam.getParamType())) {
                            String popValue = popParam.getLabelValue();
                            newDevice.setPop(popValue);
                        } else if (AppConstants.PARAM_TYPE_LOCAL_CONTROL_TYPE.equalsIgnoreCase(popParam.getParamType())) {
                            int type = (int) popParam.getValue();
                            newDevice.setSecurityType(type);
                        }
                    }
                }
            }

            final EspLocalDevice localDevice = newDevice;

            if (newDevice.getPropertyCount() == -1) {
                localControlApiManager.getPropertyCount(AppConstants.LOCAL_CONTROL_ENDPOINT, localDevice, new ApiResponseListener() {

                    @Override
                    public void onSuccess(Bundle data) {

                        if (data != null) {

                            int count = data.getInt(AppConstants.KEY_PROPERTY_COUNT, 0);
                            localDevice.setPropertyCount(count);

                            localControlApiManager.getPropertyValues(AppConstants.LOCAL_CONTROL_ENDPOINT, localDevice, new ApiResponseListener() {

                                @Override
                                public void onSuccess(Bundle data) {

                                    if (data != null) {

                                        String configData = data.getString(AppConstants.KEY_CONFIG);
                                        String paramsData = data.getString(AppConstants.KEY_PARAMS);

                                        Log.d(TAG, "Config data : " + configData);
                                        Log.d(TAG, "Params data : " + paramsData);

                                        if (!TextUtils.isEmpty(configData)) {

                                            JSONObject configJson = null;
                                            try {
                                                configJson = new JSONObject(configData);
                                            } catch (JSONException e) {
                                                e.printStackTrace();
                                            }

                                            String id = configJson.optString(AppConstants.KEY_NODE_ID);
                                            EspNode node = nodeMap.get(id);

                                            EspNode localNode = JsonDataParser.setNodeConfig(node, configJson);

                                            if (node != null) {
                                                Log.e(TAG, "Found node " + localNode.getNodeId() + " on local network.");
                                                localNode.setAvailableLocally(true);
                                                localNode.setIpAddress(localDevice.getIpAddr());
                                                localNode.setPort(localDevice.getPort());
                                                localNode.setOnline(true);
                                                localDeviceMap.put(localNode.getNodeId(), localDevice);
                                            }

                                            if (!TextUtils.isEmpty(paramsData)) {

                                                JSONObject paramsJson = null;
                                                try {
                                                    paramsJson = new JSONObject(paramsData);
                                                } catch (JSONException e) {
                                                    e.printStackTrace();
                                                }
                                                JsonDataParser.setAllParams(EspApplication.this, localNode, paramsJson);
                                                nodeMap.put(localNode.getNodeId(), localNode);
                                                EventBus.getDefault().post(new UpdateEvent(UpdateEventType.EVENT_LOCAL_DEVICE_UPDATE));
                                            }
                                        }
                                    }
                                }

                                @Override
                                public void onResponseFailure(Exception exception) {
                                    // Nothing to do
                                }

                                @Override
                                public void onNetworkFailure(Exception exception) {
                                    // Nothing to do
                                }
                            });
                        }
                    }

                    @Override
                    public void onResponseFailure(Exception exception) {
                        if (localDeviceMap.containsKey(nodeId)) {
                            Log.e(TAG, "Remove local device from list");
                            localDeviceMap.remove(nodeId);
                        }
                    }

                    @Override
                    public void onNetworkFailure(Exception exception) {
                        // Nothing to do
                    }
                });
            } else {
                Log.e(TAG, "Local device is already available and properties are already available");
            }
        }
    };

    private void setupNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel nodeConnectedChannel = new NotificationChannel(AppConstants.CHANNEL_NODE_ONLINE_ID,
                    getString(R.string.channel_node_connected), NotificationManager.IMPORTANCE_HIGH);

            NotificationChannel nodeDisconnectedChannel = new NotificationChannel(AppConstants.CHANNEL_NODE_OFFLINE_ID,
                    getString(R.string.channel_node_disconnected), NotificationManager.IMPORTANCE_HIGH);

            NotificationChannel nodeAddedChannel = new NotificationChannel(AppConstants.CHANNEL_NODE_ADDED,
                    getString(R.string.channel_node_added), NotificationManager.IMPORTANCE_HIGH);

            NotificationChannel nodeRemovedChannel = new NotificationChannel(AppConstants.CHANNEL_NODE_REMOVED,
                    getString(R.string.channel_node_removed), NotificationManager.IMPORTANCE_HIGH);

            NotificationChannel nodeSharingChannel = new NotificationChannel(AppConstants.CHANNEL_NODE_SHARING,
                    getString(R.string.channel_node_sharing), NotificationManager.IMPORTANCE_HIGH);

            NotificationChannel alertChannel = new NotificationChannel(AppConstants.CHANNEL_ALERT,
                    getString(R.string.channel_node_alert), NotificationManager.IMPORTANCE_HIGH);

            NotificationChannel automationChannel = new NotificationChannel(AppConstants.CHANNEL_NODE_AUTOMATION_TRIGGER,
                    getString(R.string.channel_node_automation_trigger), NotificationManager.IMPORTANCE_HIGH);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(nodeConnectedChannel);
            notificationManager.createNotificationChannel(nodeDisconnectedChannel);
            notificationManager.createNotificationChannel(nodeAddedChannel);
            notificationManager.createNotificationChannel(nodeRemovedChannel);
            notificationManager.createNotificationChannel(nodeSharingChannel);
            notificationManager.createNotificationChannel(alertChannel);
            notificationManager.createNotificationChannel(automationChannel);
        }
    }

    public void removeNodeInformation(String nodeId) {
        nodeMap.remove(nodeId);
        localDeviceMap.remove(nodeId);
    }

    /**
     * Check the device to make sure it has the Google Play Services APK.
     *
     * @return Returns true if Google Api is available.
     */
    private boolean isPlayServicesAvailable() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(this);
        if (resultCode == ConnectionResult.SUCCESS) {
            return true;
        }
        return false;
    }

    private boolean isParamAvailableInList(ArrayList<Param> params, String type) {
        boolean isAvailable = false;
        if (params.size() > 0) {
            for (Param p : params) {
                if (p.getParamType() != null && p.getParamType().equals(type)) {
                    isAvailable = true;
                    break;
                }
            }
        }
        return isAvailable;
    }

    private void addToggleParam(ArrayList<Param> params, ArrayList<String> properties) {
        // Add on/off param
        Param param = new Param();
        param.setDynamicParam(true);
        param.setDataType("bool");
        param.setUiType(AppConstants.UI_TYPE_TOGGLE);
        param.setParamType(AppConstants.PARAM_TYPE_POWER);
        param.setName("Power");
        param.setSwitchStatus(false);
        param.setProperties(properties);
        params.add(param);
    }
}
