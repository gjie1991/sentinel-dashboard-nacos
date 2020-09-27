/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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
 */
package com.alibaba.csp.sentinel.dashboard.nacos;

import com.alibaba.csp.sentinel.dashboard.discovery.AppInfo;
import com.alibaba.csp.sentinel.dashboard.discovery.MachineDiscovery;
import com.alibaba.csp.sentinel.dashboard.discovery.MachineInfo;
import com.alibaba.csp.sentinel.util.AssertUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.api.config.ConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author leyou
 */
@Component
public class NacosMachineDiscovery implements MachineDiscovery {

    @Autowired
    private ConfigService configService;

    @Autowired
    private NacosConfigProperties nacosConfigProperties;

    private Set<String> getApps() {
        try {
            String appsJson = configService.getConfig(NacosConfigUtil.APPS, nacosConfigProperties.getGroupId(), 5000);
            if (appsJson == null) {
                return new HashSet<>();
            }
            Set<String> apps = new HashSet<>(JSON.parseArray(appsJson, String.class));
            return apps;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setApps(Set<String> apps) {
        try {
            String appsJson = JSON.toJSONString(apps);
            configService.publishConfig(NacosConfigUtil.APPS, nacosConfigProperties.getGroupId(), appsJson);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private AppInfo getAppInfo(String app) {
        try {
            String appinfoJson = configService.getConfig(app + NacosConfigUtil.APPINFO_POSTFIX, nacosConfigProperties.getGroupId(), 5000);
            if (appinfoJson == null) {
                return null;
            }
            AppInfo appInfo = JSON.parseObject(appinfoJson, AppInfo.class);
            return appInfo;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void setAppInfo(AppInfo appInfo) {
        try {
            String appInfoJson = JSON.toJSONString(appInfo);
            configService.publishConfig(appInfo.getApp() + NacosConfigUtil.APPINFO_POSTFIX, nacosConfigProperties.getGroupId(), appInfoJson);
            Set<String> apps = getApps();
            apps.add(appInfo.getApp());
            setApps(apps);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public long addMachine(MachineInfo machineInfo) {
        AssertUtil.notNull(machineInfo, "machineInfo cannot be null");
        AppInfo appinfo = getAppInfo(machineInfo.getApp());
        if (appinfo == null) {
            appinfo = new AppInfo(machineInfo.getApp(), machineInfo.getAppType());
        }
        appinfo.addMachine(machineInfo);
        setAppInfo(appinfo);
        return 1;
    }

    @Override
    public boolean removeMachine(String app, String ip, int port) {
        AssertUtil.assertNotBlank(app, "app name cannot be blank");
        AppInfo appInfo = getAppInfo(app);
        if (appInfo != null) {
            if (appInfo.removeMachine(ip, port)) {
                setAppInfo(appInfo);
                return Boolean.TRUE;
            }
        }
        return false;
    }

    @Override
    public List<String> getAppNames() {
        return new ArrayList<>(getApps());
    }

    @Override
    public AppInfo getDetailApp(String app) {
        AssertUtil.assertNotBlank(app, "app name cannot be blank");
        return getAppInfo(app);
    }

    @Override
    public Set<AppInfo> getBriefApps() {
        return getApps().stream().map(app ->
                getAppInfo(app)
        ).collect(Collectors.toSet());
    }

    @Override
    public void removeApp(String app) {
        AssertUtil.assertNotBlank(app, "app name cannot be blank");
        Set<String> apps = getApps();
        apps.remove(app);
        setApps(apps);
        try {
            configService.removeConfig(app + NacosConfigUtil.APPINFO_POSTFIX, nacosConfigProperties.getGroupId());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

}
