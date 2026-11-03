// role/package-info.java
@ApplicationModule(
        allowedDependencies = {
                "menu",                 // MenuFacade.existsByIds
                "menu::events",         // MenuDeletedEvent
                "organization",         // OrganizationFacade.existsByIds
                "organization::events", // DepartmentDeletedEvent
                "shared"
        }
)
package cn.realm.cloud.system.server.role;

import org.springframework.modulith.ApplicationModule;
