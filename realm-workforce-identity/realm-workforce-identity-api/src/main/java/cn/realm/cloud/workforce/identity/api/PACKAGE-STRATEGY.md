📁 realm-workforce-identity-api 📁 详细包结构全景

realm-workforce-identity-api/
└── src/main/java/cn/realm/cloud/workforce/identity/api/
├── package-info.java               
│
├── facade/                         # 同进程调用 (Java)
│   ├── IdentityFacade.java
│   └── dto/
│       └── UserDto.java
│
├── remote/                         # 跨进程调用 (HTTP/Feign)
│   ├── UserApi.java                # 带有 @GetMapping 等注解
│   └── dto/
│       └── UserResponse.java
│
└── events/                         # 异步通信
├── package-info.java
└── UserRegisteredEvent.java


realm-workforce-identity-api/
└── src/main/java/cn/realm/cloud/workforce/identity/api/
├── facade/
├── events/
└── remote/                         # 跨进程调用契约 (HTTP)
└── user/
├── UserApi.java            # 接口定义
└── dto/
├── UserPageRequest.java
└── UserResponse.java
