// organization/package-info.java
@ApplicationModule(
        allowedDependencies = {"user", "shared", "organization::events"}
)
package cn.realm.cloud.workforce.identity.internal.organization;

import org.springframework.modulith.ApplicationModule;
