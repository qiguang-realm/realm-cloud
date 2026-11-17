// user/package-info.java
@ApplicationModule(
        allowedDependencies = {
                "role",                 // RoleFacade.existsByIds / findByIds
                "role::events",         // RoleDeletedEvent
                "organization",         // OrganizationFacade.existsById
                "organization::events", // DepartmentDeletedEvent
                "permission",
                "shared"
        }
)
package cn.realm.cloud.workforce.identity.internal.user;

import org.springframework.modulith.ApplicationModule;
