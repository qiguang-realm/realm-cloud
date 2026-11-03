// organization/package-info.java
@ApplicationModule(
        allowedDependencies = {"user", "shared", "organization::events"}
)
package cn.realm.cloud.system.server.organization;

import org.springframework.modulith.ApplicationModule;
