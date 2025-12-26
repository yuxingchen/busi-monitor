# 易扩展、易维护Java工程代码要求逐条详解

## 一、核心设计原则

### 1.1 SOLID原则

**单一职责原则** - 每个类只有一个变化的原因
```java
// ❌ 违反：类处理过多职责
class OrderProcessor {
    public void processOrder(Order order) {
        validate(order);          // 验证逻辑
        saveToDatabase(order);    // 数据持久化
        sendEmail(order);         // 通知发送
        updateInventory(order);   // 库存更新
    }
}

// ✅ 遵循：拆分为单一职责的类
class OrderValidator {
    public void validate(Order order) { ... }
}

class OrderRepository {
    public void save(Order order) { ... }
}

class NotificationService {
    public void sendOrderConfirmation(Order order) { ... }
}

class InventoryService {
    public void update(Order order) { ... }
}
```

**开闭原则** - 对扩展开放，对修改关闭
```java
// ❌ 违反：新增支付方式需要修改现有代码
class PaymentProcessor {
    public void process(String paymentType, BigDecimal amount) {
        if ("CREDIT_CARD".equals(paymentType)) {
            // 处理信用卡
        } else if ("PAYPAL".equals(paymentType)) {
            // 处理PayPal
        }
        // 新增支付方式：必须修改此方法
    }
}

// ✅ 遵循：使用策略模式
interface PaymentStrategy {
    void process(BigDecimal amount);
}

class CreditCardPayment implements PaymentStrategy {
    public void process(BigDecimal amount) { ... }
}

class PayPalPayment implements PaymentStrategy {
    public void process(BigDecimal amount) { ... }
}

class PaymentProcessor {
    private final Map<String, PaymentStrategy> strategies;
    
    public void process(String paymentType, BigDecimal amount) {
        strategies.get(paymentType).process(amount);
    }
    
    // 新增支付方式：只需添加新策略实现
    public void registerStrategy(String type, PaymentStrategy strategy) {
        strategies.put(type, strategy);
    }
}
```

**里氏替换原则** - 子类可以替换父类而不影响程序
```java
// ❌ 违反：子类改变了父类行为
class Rectangle {
    protected int width;
    protected int height;
    
    public void setWidth(int width) { this.width = width; }
    public void setHeight(int height) { this.height = height; }
    public int getArea() { return width * height; }
}

class Square extends Rectangle {
    @Override
    public void setWidth(int width) {
        super.setWidth(width);
        super.setHeight(width); // 违反：改变了行为
    }
    
    @Override
    public void setHeight(int height) {
        super.setHeight(height);
        super.setWidth(height); // 违反：改变了行为
    }
}

// ✅ 遵循：重新设计继承关系
interface Shape {
    int getArea();
}

class Rectangle implements Shape {
    private int width;
    private int height;
    // getters/setters
    public int getArea() { return width * height; }
}

class Square implements Shape {
    private int side;
    public void setSide(int side) { this.side = side; }
    public int getArea() { return side * side; }
}
```

**接口隔离原则** - 客户端不应依赖不需要的接口
```java
// ❌ 违反：庞大的接口
interface Worker {
    void work();
    void eat();
    void sleep();
}

class Robot implements Worker {
    public void work() { ... }
    public void eat() { 
        throw new UnsupportedOperationException(); // 机器人不需要吃饭
    }
    public void sleep() { 
        throw new UnsupportedOperationException(); // 机器人不需要睡觉
    }
}

// ✅ 遵循：接口拆分
interface Workable {
    void work();
}

interface Eatable {
    void eat();
}

interface Sleepable {
    void sleep();
}

class Human implements Workable, Eatable, Sleepable {
    public void work() { ... }
    public void eat() { ... }
    public void sleep() { ... }
}

class Robot implements Workable {
    public void work() { ... }
}
```

**依赖倒置原则** - 依赖抽象而非具体实现
```java
// ❌ 违反：高层模块依赖低层模块
class EmailService {
    public void sendEmail(String message) { ... }
}

class NotificationService {
    private EmailService emailService = new EmailService();
    
    public void notify(String message) {
        emailService.sendEmail(message); // 直接依赖具体实现
    }
}

// ✅ 遵循：依赖抽象
interface MessageSender {
    void send(String message);
}

class EmailService implements MessageSender {
    public void send(String message) { ... }
}

class SMSService implements MessageSender {
    public void send(String message) { ... }
}

class NotificationService {
    private final MessageSender sender;
    
    public NotificationService(MessageSender sender) { // 依赖抽象
        this.sender = sender;
    }
    
    public void notify(String message) {
        sender.send(message);
    }
}
```

### 1.2 DRY原则 - 避免重复代码
```java
// ❌ 违反：重复的验证逻辑
class UserService {
    public void createUser(User user) {
        if (user.getName() == null || user.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Name不能为空");
        }
        if (user.getEmail() == null || !user.getEmail().contains("@")) {
            throw new IllegalArgumentException("Email格式错误");
        }
        // 业务逻辑
    }
    
    public void updateUser(User user) {
        if (user.getName() == null || user.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Name不能为空");
        }
        if (user.getEmail() == null || !user.getEmail().contains("@")) {
            throw new IllegalArgumentException("Email格式错误");
        }
        // 更新逻辑
    }
}

// ✅ 遵循：提取公共验证方法
class UserValidator {
    public void validateUser(User user) {
        validateName(user.getName());
        validateEmail(user.getEmail());
    }
    
    private void validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Name不能为空");
        }
    }
    
    private void validateEmail(String email) {
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("Email格式错误");
        }
    }
}

class UserService {
    private final UserValidator validator = new UserValidator();
    
    public void createUser(User user) {
        validator.validateUser(user);
        // 业务逻辑
    }
    
    public void updateUser(User user) {
        validator.validateUser(user);
        // 更新逻辑
    }
}
```

### 1.3 KISS原则 - 保持简单
```java
// ❌ 违反：过度设计
interface OrderProcessor {
    OrderResult process(Order order);
}

abstract class AbstractOrderProcessor implements OrderProcessor {
    protected abstract void validate(Order order);
    protected abstract void execute(Order order);
    
    public OrderResult process(Order order) {
        validate(order);
        execute(order);
        return createResult(order);
    }
    
    private OrderResult createResult(Order order) {
        return new OrderResult();
    }
}

class StandardOrderProcessor extends AbstractOrderProcessor {
    protected void validate(Order order) { ... }
    protected void execute(Order order) { ... }
}

// ✅ 遵循：简单直接的实现
class OrderService {
    public OrderResult processOrder(Order order) {
        validateOrder(order);
        saveOrder(order);
        updateInventory(order);
        return new OrderResult(order.getId(), "SUCCESS");
    }
    
    private void validateOrder(Order order) { ... }
    private void saveOrder(Order order) { ... }
    private void updateInventory(Order order) { ... }
}
```

## 二、架构与分层

### 2.1 清晰的分层架构
```java
// 控制器层 - 处理HTTP请求
@RestController
@RequestMapping("/api/orders")
class OrderController {
    private final OrderService orderService;
    
    @PostMapping
    public ResponseEntity<OrderDto> createOrder(@RequestBody OrderDto orderDto) {
        OrderDto createdOrder = orderService.createOrder(orderDto);
        return ResponseEntity.created(URI.create("/orders/" + createdOrder.getId()))
                           .body(createdOrder);
    }
}

// 业务逻辑层 - 处理业务规则
@Service
class OrderService {
    private final OrderRepository orderRepository;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    
    public OrderDto createOrder(OrderDto orderDto) {
        Order order = convertToEntity(orderDto);
        validateOrder(order);
        processPayment(order);
        Order savedOrder = orderRepository.save(order);
        notificationService.sendOrderConfirmation(savedOrder);
        return convertToDto(savedOrder);
    }
}

// 数据访问层 - 处理数据持久化
@Repository
class OrderRepository {
    @PersistenceContext
    private EntityManager entityManager;
    
    public Order save(Order order) {
        entityManager.persist(order);
        return order;
    }
}

// 外部集成层 - 集成外部服务
@Component
class PaymentService {
    private final RestTemplate restTemplate;
    
    public void processPayment(Order order) {
        PaymentRequest request = createPaymentRequest(order);
        PaymentResponse response = restTemplate.postForObject(
            "https://payment-gateway/api/charge", 
            request, 
            PaymentResponse.class
        );
        validatePaymentResponse(response);
    }
}
```

### 2.2 模块化设计
```
project/
├── user-module/          # 用户管理模块
│   ├── src/
│   │   ├── main/java/com/example/user/
│   │   │   ├── domain/           # 领域层
│   │   │   │   ├── User.java
│   │   │   │   ├── UserRepository.java
│   │   │   │   └── UserService.java
│   │   │   ├── application/      # 应用层
│   │   │   │   └── UserApplicationService.java
│   │   │   └── infrastructure/   # 基础设施层
│   │   │       └── JpaUserRepository.java
│   │   └── resources/
│   │       └── application-user.yml
│   └── pom.xml
│
├── order-module/         # 订单管理模块
│   ├── src/
│   │   ├── main/java/com/example/order/
│   │   │   ├── domain/
│   │   │   ├── application/
│   │   │   └── infrastructure/
│   │   └── resources/
│   └── pom.xml
│
├── payment-module/       # 支付模块
│   └── ...
│
└── pom.xml               # 父POM管理公共依赖
```

### 2.3 领域驱动设计元素
```java
// 实体 - 有唯一标识
@Entity
class User {
    @Id
    private UserId id;          // 值对象作为ID
    private String name;
    private Email email;        // 值对象
    private List<Role> roles;   // 值对象集合
    
    // 业务方法
    public void changeEmail(Email newEmail) {
        validateEmailChange(newEmail);
        this.email = newEmail;
        this.addDomainEvent(new UserEmailChangedEvent(this.id, newEmail));
    }
}

// 值对象 - 无标识，不可变
@Embeddable
class Email {
    private final String value;
    
    public Email(String value) {
        validate(value);
        this.value = value;
    }
    
    public String getValue() { return value; }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Email)) return false;
        Email email = (Email) o;
        return Objects.equals(value, email.value);
    }
}

// 聚合根 - 聚合的入口点
@Entity
class Order {
    @Id
    private OrderId id;
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items;      // 聚合内的实体
    private OrderStatus status;
    
    // 聚合根控制业务逻辑
    public void addItem(Product product, int quantity) {
        OrderItem item = new OrderItem(product, quantity);
        this.items.add(item);
    }
    
    public void cancel() {
        if (!status.canCancel()) {
            throw new IllegalStateException("订单无法取消");
        }
        this.status = OrderStatus.CANCELLED;
        this.addDomainEvent(new OrderCancelledEvent(this.id));
    }
}

// 领域服务 - 处理跨聚合的业务逻辑
@Service
class TransferService {
    public void transfer(Account from, Account to, Money amount) {
        from.withdraw(amount);
        to.deposit(amount);
        // 记录转账事件
    }
}
```

## 三、代码质量要求

### 3.1 命名规范清晰
```java
// ✅ 好的命名示例
class CustomerOrderProcessor {
    // 类名：名词，清晰表达职责
    private final OrderRepository orderRepository;
    private final PaymentGateway paymentGateway;
    
    public OrderResult processCustomerOrder(OrderRequest request) {
        // 方法名：动词+名词，清晰表达行为
        Customer customer = findCustomer(request.getCustomerId());
        validateOrderRequest(request);
        Order order = createOrder(request, customer);
        processPayment(order);
        return generateOrderResult(order);
    }
    
    private boolean isValidCustomer(Customer customer) {
        // 布尔方法：is/has/can开头
        return customer != null && customer.isActive();
    }
    
    private static final int MAX_RETRY_ATTEMPTS = 3;
    // 常量：全大写，下划线分隔
}

// ❌ 差的命名示例
class Proc {  // 缩写不明确
    private Repo r;  // 无意义缩写
    private PG pg;   // 含义不清
    
    public void do(Req req) {  // 方法名太泛
        Cust c = findC(req.getCid());  // 变量名缩写
        // ...
    }
}
```

### 3.2 函数设计良好
```java
// ✅ 好的函数设计
public class OrderValidator {
    
    // 函数短小（<20行）
    public ValidationResult validate(Order order) {
        if (order == null) {
            return ValidationResult.error("订单不能为空");
        }
        
        validateCustomer(order.getCustomer());
        validateItems(order.getItems());
        validateTotalAmount(order.getTotalAmount());
        
        return ValidationResult.success();
    }
    
    // 单一职责
    private void validateCustomer(Customer customer) {
        if (customer == null) {
            throw new IllegalArgumentException("客户信息缺失");
        }
        if (!customer.isActive()) {
            throw new BusinessException("客户账户已冻结");
        }
    }
    
    // 合适的抽象层级
    private void validateItems(List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("订单项目不能为空");
        }
        
        for (OrderItem item : items) {
            validateItemQuantity(item);
            validateItemPrice(item);
        }
    }
    
    // 避免副作用
    private void validateTotalAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("订单金额无效");
        }
        // 不修改amount，只做验证
    }
}

// ❌ 差的函数设计
public void processEverything(Order order, Customer customer, PaymentInfo payment, 
                             boolean sendEmail, boolean updateInventory, 
                             boolean logEverything) {
    // 函数太长，做了太多事
    // 参数太多
    // 布尔参数表示做不同事情（应拆分为多个函数）
}
```

### 3.3 类设计合理
```java
// ✅ 好的类设计
// 类大小适中（<300行）
public class ShoppingCart {
    private final List<CartItem> items = new ArrayList<>();
    private final Customer customer;
    private final DateTime createdAt;
    
    // 明确的责任划分
    public void addItem(Product product, int quantity) {
        CartItem existingItem = findItemByProduct(product);
        if (existingItem != null) {
            existingItem.increaseQuantity(quantity);
        } else {
            items.add(new CartItem(product, quantity));
        }
    }
    
    public void removeItem(Product product) {
        items.removeIf(item -> item.getProduct().equals(product));
    }
    
    public BigDecimal calculateTotal() {
        return items.stream()
            .map(CartItem::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    // 良好的封装性
    private CartItem findItemByProduct(Product product) {
        return items.stream()
            .filter(item -> item.getProduct().equals(product))
            .findFirst()
            .orElse(null);
    }
    
    // 不变性保障
    public List<CartItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}

// ❌ 差的类设计
public class GodClass {
    // 太多字段
    private String field1, field2, field3, /* ... */ field50;
    
    // 太多方法，职责混乱
    public void processOrder() { ... }
    public void sendEmail() { ... }
    public void updateDatabase() { ... }
    public void generateReport() { ... }
    public void calculateTax() { ... }
    // ... 还有很多方法
}
```

## 四、依赖管理

### 4.1 依赖注入
```java
// ✅ 使用构造器注入
@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final PaymentService paymentService;
    private final InventoryService inventoryService;
    private final NotificationService notificationService;
    
    // 构造器注入（推荐）
    public OrderService(OrderRepository orderRepository,
                       PaymentService paymentService,
                       InventoryService inventoryService,
                       NotificationService notificationService) {
        this.orderRepository = orderRepository;
        this.paymentService = paymentService;
        this.inventoryService = inventoryService;
        this.notificationService = notificationService;
    }
    
    // Setter注入（可选依赖时使用）
    private DiscountCalculator discountCalculator;
    
    @Autowired(required = false)
    public void setDiscountCalculator(DiscountCalculator discountCalculator) {
        this.discountCalculator = discountCalculator;
    }
}

// ❌ 避免紧耦合的依赖
public class BadOrderService {
    // 直接在类中创建依赖
    private OrderRepository orderRepository = new JpaOrderRepository();
    private PaymentService paymentService = new PaymentServiceImpl();
    
    // 或者使用静态工厂
    private NotificationService notificationService = 
        ServiceFactory.getNotificationService();
}
```

### 4.2 控制反转容器配置
```java
// 使用Java Config进行Bean配置
@Configuration
public class AppConfig {
    
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(env.getProperty("db.url"));
        config.setUsername(env.getProperty("db.username"));
        config.setPassword(env.getProperty("db.password"));
        return new HikariDataSource(config);
    }
    
    @Bean
    public JpaTransactionManager transactionManager(EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }
    
    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(new CustomErrorHandler());
        restTemplate.setInterceptors(List.of(new LoggingInterceptor()));
        return restTemplate;
    }
}

// 使用条件化Bean
@Configuration
@ConditionalOnProperty(name = "cache.enabled", havingValue = "true")
public class CacheConfig {
    
    @Bean
    @ConditionalOnMissingBean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("products", "users");
    }
}
```

## 五、配置管理

### 5.1 外部化配置
```yaml
# application.yml - 主配置文件
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/mydb
    username: ${DB_USERNAME:default_user}
    password: ${DB_PASSWORD:}
    hikari:
      maximum-pool-size: 20
      connection-timeout: 30000

app:
  security:
    jwt:
      secret-key: ${JWT_SECRET:default-secret-key}
      expiration: 86400000  # 24小时
      
  payment:
    gateway:
      url: https://api.payment.com/v1
      api-key: ${PAYMENT_API_KEY}
      timeout: 5000
      retry:
        max-attempts: 3
        backoff-delay: 1000
        
  cache:
    ttl:
      products: 3600     # 1小时
      users: 1800       # 30分钟
      
  rate-limiting:
    requests-per-minute: 100
    burst-size: 20
```

### 5.2 环境特定配置
```yaml
# application-dev.yml - 开发环境
spring:
  datasource:
    url: jdbc:h2:mem:testdb
  jpa:
    show-sql: true
    hibernate:
      ddl-auto: update
      
app:
  payment:
    gateway:
      url: http://localhost:8081/mock-payment
  logging:
    level:
      com.example: DEBUG

# application-prod.yml - 生产环境
spring:
  datasource:
    url: jdbc:mysql://prod-db:3306/production
  jpa:
    hibernate:
      ddl-auto: validate
      
app:
  payment:
    gateway:
      url: https://api.payment.com/v1
  monitoring:
    enabled: true
  logging:
    level:
      com.example: INFO
```

## 六、测试策略

### 6.1 单元测试示例
```java
// 使用JUnit 5和Mockito
class OrderServiceTest {
    
    @Mock
    private OrderRepository orderRepository;
    
    @Mock
    private PaymentService paymentService;
    
    @Mock
    private InventoryService inventoryService;
    
    @InjectMocks
    private OrderService orderService;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }
    
    @Test
    @DisplayName("创建订单成功")
    void createOrder_success() {
        // Given - 测试准备
        OrderRequest request = createValidOrderRequest();
        Order expectedOrder = createExpectedOrder();
        
        when(orderRepository.save(any(Order.class)))
            .thenReturn(expectedOrder);
        doNothing().when(paymentService).process(any(PaymentRequest.class));
        
        // When - 执行测试
        OrderResult result = orderService.createOrder(request);
        
        // Then - 验证结果
        assertThat(result)
            .isNotNull()
            .hasFieldOrPropertyWithValue("status", "SUCCESS");
        
        verify(paymentService, times(1)).process(any(PaymentRequest.class));
        verify(orderRepository, times(1)).save(any(Order.class));
    }
    
    @Test
    @DisplayName("库存不足时创建订单失败")
    void createOrder_failWhenInsufficientInventory() {
        // Given
        OrderRequest request = createOrderRequestWithLargeQuantity();
        
        doThrow(new InventoryException("库存不足"))
            .when(inventoryService).reserve(any(OrderItem.class));
        
        // When & Then
        assertThatThrownBy(() -> orderService.createOrder(request))
            .isInstanceOf(InventoryException.class)
            .hasMessage("库存不足");
    }
    
    @ParameterizedTest
    @CsvSource({
        "100, 10, 90",
        "200, 20, 180",
        "50, 0, 50"
    })
    @DisplayName("计算折扣价格")
    void calculateDiscountedPrice(double original, double discount, double expected) {
        BigDecimal result = orderService.calculateDiscountedPrice(
            BigDecimal.valueOf(original), 
            BigDecimal.valueOf(discount)
        );
        
        assertThat(result)
            .isEqualByComparingTo(BigDecimal.valueOf(expected));
    }
}
```

### 6.2 集成测试示例
```java
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class OrderIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Test
    @Transactional
    void createOrderIntegrationTest() throws Exception {
        // Given
        OrderRequest request = new OrderRequest();
        request.setCustomerId(1L);
        request.setItems(List.of(
            new OrderItemRequest(101L, 2)
        ));
        
        // When
        MvcResult result = mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        
        // Then
        OrderResponse response = objectMapper.readValue(
            result.getResponse().getContentAsString(),
            OrderResponse.class
        );
        
        assertThat(response.getId()).isNotNull();
        assertThat(response.getStatus()).isEqualTo("PROCESSING");
        
        // 验证数据库
        Optional<Order> savedOrder = orderRepository.findById(response.getId());
        assertThat(savedOrder).isPresent();
        assertThat(savedOrder.get().getItems()).hasSize(1);
    }
}
```

## 七、异常处理

### 7.1 自定义异常体系
```java
// 基础业务异常
public abstract class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;
    private final Map<String, Object> context;
    
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.context = new HashMap<>();
    }
    
    public BusinessException withContext(String key, Object value) {
        this.context.put(key, value);
        return this;
    }
}

// 具体业务异常
public class OrderNotFoundException extends BusinessException {
    public OrderNotFoundException(Long orderId) {
        super(ErrorCode.ORDER_NOT_FOUND, 
              String.format("订单ID %d 不存在", orderId));
        withContext("orderId", orderId);
    }
}

public class InsufficientInventoryException extends BusinessException {
    public InsufficientInventoryException(Long productId, int requested, int available) {
        super(ErrorCode.INSUFFICIENT_INVENTORY,
              String.format("产品 %d 库存不足，请求: %d，可用: %d", 
                           productId, requested, available));
        withContext("productId", productId)
            .withContext("requested", requested)
            .withContext("available", available);
    }
}

// 错误码枚举
public enum ErrorCode {
    ORDER_NOT_FOUND("ORDER-001", "订单不存在"),
    INSUFFICIENT_INVENTORY("INVENTORY-001", "库存不足"),
    PAYMENT_FAILED("PAYMENT-001", "支付失败"),
    UNAUTHORIZED_ACCESS("AUTH-001", "未授权访问");
    
    private final String code;
    private final String message;
    
    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
```

### 7.2 全局异常处理
```java
@ControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException ex, HttpServletRequest request) {
        
        log.warn("业务异常: {} - {}", ex.getErrorCode(), ex.getMessage(), ex);
        
        ErrorResponse error = ErrorResponse.builder()
            .timestamp(Instant.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
            .message(ex.getMessage())
            .errorCode(ex.getErrorCode().getCode())
            .path(request.getRequestURI())
            .context(ex.getContext())
            .build();
            
        return ResponseEntity.badRequest().body(error);
    }
    
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            ConstraintViolationException ex) {
        
        List<FieldError> fieldErrors = ex.getConstraintViolations().stream()
            .map(violation -> new FieldError(
                getFieldName(violation.getPropertyPath()),
                violation.getMessage()
            ))
            .collect(Collectors.toList());
        
        ErrorResponse error = ErrorResponse.builder()
            .message("参数验证失败")
            .fieldErrors(fieldErrors)
            .build();
            
        return ResponseEntity.badRequest().body(error);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {
        
        log.error("未处理的异常: {}", request.getRequestURI(), ex);
        
        ErrorResponse error = ErrorResponse.builder()
            .message("系统内部错误，请稍后重试")
            .path(request.getRequestURI())
            .build();
            
        return ResponseEntity.internalServerError().body(error);
    }
}

// 错误响应DTO
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ErrorResponse {
    private Instant timestamp;
    private int status;
    private String error;
    private String message;
    private String errorCode;
    private String path;
    private Map<String, Object> context;
    private List<FieldError> fieldErrors;
    
    @Data
    @AllArgsConstructor
    public static class FieldError {
        private String field;
        private String message;
    }
}
```

## 八、文档与注释

### 8.1 API文档（OpenAPI/Swagger）
```java
@RestController
@RequestMapping("/api/users")
@Tag(name = "用户管理", description = "用户相关操作API")
public class UserController {
    
    @Operation(
        summary = "创建用户",
        description = "创建新用户并返回用户信息"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "用户创建成功",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = UserDto.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "请求参数无效"
        ),
        @ApiResponse(
            responseCode = "409",
            description = "用户已存在"
        )
    })
    @PostMapping
    public ResponseEntity<UserDto> createUser(
            @Parameter(
                description = "用户信息",
                required = true
            )
            @Valid @RequestBody CreateUserRequest request) {
        // 实现
    }
    
    @Operation(
        summary = "获取用户列表",
        description = "分页获取用户列表，支持按姓名搜索"
    )
    @GetMapping
    public Page<UserDto> getUsers(
            @Parameter(description = "页码，从0开始") 
            @RequestParam(defaultValue = "0") int page,
            
            @Parameter(description = "每页大小") 
            @RequestParam(defaultValue = "20") int size,
            
            @Parameter(description = "排序字段，格式: field,asc|desc")
            @RequestParam(defaultValue = "id,asc") String sort,
            
            @Parameter(description = "搜索关键词")
            @RequestParam(required = false) String keyword) {
        // 实现
    }
}
```

### 8.2 代码注释示例
```java
/**
 * 订单服务类
 * 
 * <p>负责处理订单相关的核心业务逻辑，包括：
 * <ul>
 *   <li>订单创建与验证</li>
 *   <li>订单状态管理</li>
 *   <li>订单支付处理</li>
 *   <li>订单取消与退款</li>
 * </ul>
 * 
 * @author Developer
 * @since 1.0.0
 * @see Order
 * @see OrderRepository
 */
@Service
@Transactional
@Slf4j
public class OrderService {
    
    /**
     * 创建订单
     * 
     * <p>处理订单创建的全流程，包括：
     * <ol>
     *   <li>验证订单信息</li>
     *   <li>检查库存</li>
     *   <li>处理支付</li>
     *   <li>保存订单</li>
     *   <li>发送通知</li>
     * </ol>
     * 
     * @param request 订单请求，包含订单详细信息
     * @return 创建成功的订单结果
     * @throws InvalidOrderException 当订单信息无效时
     * @throws InsufficientInventoryException 当库存不足时
     * @throws PaymentFailedException 当支付失败时
     * 
     * @example
     * <pre>{@code
     * OrderRequest request = new OrderRequest();
     * request.setCustomerId(123L);
     * request.setItems(List.of(item1, item2));
     * 
     * OrderResult result = orderService.createOrder(request);
     * assert result.getStatus() == OrderStatus.CREATED;
     * }</pre>
     */
    public OrderResult createOrder(OrderRequest request) {
        // 1. 参数校验
        validateRequest(request);
        
        // 2. 检查库存
        checkInventory(request.getItems());
        
        // 3. 处理支付
        PaymentResult paymentResult = processPayment(request);
        
        // 4. 创建订单实体
        Order order = buildOrder(request, paymentResult);
        
        // 5. 保存订单
        Order savedOrder = orderRepository.save(order);
        
        // 6. 发送通知
        notificationService.sendOrderCreated(savedOrder);
        
        return buildOrderResult(savedOrder);
    }
    
    /**
     * 计算订单折扣
     * 
     * <p>根据促销规则计算订单折扣金额，支持：
     * <ul>
     *   <li>满减优惠</li>
     *   <li>折扣券</li>
     *   <li>会员折扣</li>
     *   <li>组合优惠</li>
     * </ul>
     * 
     * @param order 订单信息（不能为null）
     * @param promotions 促销规则列表（可为空）
     * @return 折扣金额，不会返回null，无折扣时返回{@link BigDecimal#ZERO}
     * @throws IllegalArgumentException 当order为null时
     */
    public BigDecimal calculateDiscount(Order order, List<Promotion> promotions) {
        Objects.requireNonNull(order, "订单不能为空");
        
        if (CollectionUtils.isEmpty(promotions)) {
            return BigDecimal.ZERO;
        }
        
        // 应用所有符合条件的促销规则
        return promotions.stream()
            .filter(promotion -> promotion.isApplicable(order))
            .map(promotion -> promotion.calculateDiscount(order))
            .max(BigDecimal::compareTo)  // 取最大折扣
            .orElse(BigDecimal.ZERO);
    }
    
    // 复杂算法的详细注释
    private BigDecimal applyComplexDiscountAlgorithm(Order order, Promotion promotion) {
        /*
         * 复杂折扣算法说明：
         * 1. 计算基础折扣：根据订单金额分段计算
         *    0-100: 无折扣
         *    100-500: 5%折扣
         *    500以上: 10%折扣
         * 2. 应用额外优惠：如果购买指定商品，额外5%折扣
         * 3. 限制最高折扣：不超过订单金额的30%
         * 
         * 算法复杂度: O(n)，其中n为订单商品数量
         */
        
        BigDecimal orderAmount = order.getTotalAmount();
        BigDecimal baseDiscount = calculateBaseDiscount(orderAmount);
        
        BigDecimal extraDiscount = calculateExtraDiscount(order.getItems());
        
        BigDecimal totalDiscount = baseDiscount.add(extraDiscount);
        BigDecimal maxDiscount = orderAmount.multiply(new BigDecimal("0.3"));
        
        return totalDiscount.min(maxDiscount);
    }
}
```

## 九、构建与部署

### 9.1 Maven多模块配置
```xml
<!-- 父POM管理公共配置 -->
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>ecommerce-platform</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>
    
    <modules>
        <module>user-service</module>
        <module>order-service</module>
        <module>product-service</module>
        <module>common-lib</module>
    </modules>
    
    <properties>
        <java.version>17</java.version>
        <spring-boot.version>3.1.0</spring-boot.version>
        <maven.compiler.source>${java.version}</maven.compiler.source>
        <maven.compiler.target>${java.version}</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring-boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
    
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.11.0</version>
                <configuration>
                    <parameters>true</parameters>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                            <version>1.18.28</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
            
            <!-- 代码质量检查插件 -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-checkstyle-plugin</artifactId>
                <version>3.3.0</version>
                <configuration>
                    <configLocation>google_checks.xml</configLocation>
                </configuration>
                <executions>
                    <execution>
                        <phase>validate</phase>
                        <goals>
                            <goal>check</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
            
            <!-- 单元测试插件 -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.1.2</version>
                <configuration>
                    <includes>
                        <include>**/*Test.java</include>
                    </includes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### 9.2 CI/CD流水线配置（Jenkinsfile示例）
```groovy
pipeline {
    agent any
    
    environment {
        DOCKER_REGISTRY = 'registry.example.com'
        PROJECT_NAME = 'ecommerce-platform'
        VERSION = "${BUILD_NUMBER}"
    }
    
    stages {
        stage('代码检查') {
            steps {
                sh 'mvn checkstyle:check'
                sh 'mvn pmd:check'
                sh 'mvn spotbugs:check'
            }
        }
        
        stage('单元测试') {
            steps {
                sh 'mvn test'
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                }
            }
        }
        
        stage('集成测试') {
            steps {
                sh 'mvn verify -Pintegration-test'
            }
        }
        
        stage('构建镜像') {
            steps {
                script {
                    docker.build("${DOCKER_REGISTRY}/${PROJECT_NAME}:${VERSION}")
                }
            }
        }
        
        stage('安全扫描') {
            steps {
                sh "docker scan ${DOCKER_REGISTRY}/${PROJECT_NAME}:${VERSION}"
            }
        }
        
        stage('部署到测试环境') {
            when {
                branch 'develop'
            }
            steps {
                sh "kubectl set image deployment/${PROJECT_NAME} ${PROJECT_NAME}=${DOCKER_REGISTRY}/${PROJECT_NAME}:${VERSION}"
            }
        }
        
        stage('部署到生产环境') {
            when {
                branch 'main'
            }
            steps {
                input message: '确认部署到生产环境？'
                sh "kubectl set image deployment/${PROJECT_NAME}-prod ${PROJECT_NAME}=${DOCKER_REGISTRY}/${PROJECT_NAME}:${VERSION}"
            }
        }
    }
    
    post {
        success {
            emailext(
                subject: "构建成功: ${PROJECT_NAME} #${BUILD_NUMBER}",
                body: "构建成功，详情请查看: ${BUILD_URL}",
                to: 'team@example.com'
            )
        }
        failure {
            emailext(
                subject: "构建失败: ${PROJECT_NAME} #${BUILD_NUMBER}",
                body: "构建失败，详情请查看: ${BUILD_URL}",
                to: 'team@example.com'
            )
        }
    }
}
```

## 十、监控与日志

### 10.1 结构化日志配置
```java
// Logback配置文件
// logback-spring.xml
<configuration>
    <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <timestampPattern>yyyy-MM-dd'T'HH:mm:ss.SSSXXX</timestampPattern>
            <customFields>{"app":"ecommerce-platform","env":"${ENV:-local}"}</customFields>
        </encoder>
    </appender>
    
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/application.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/application.%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <root level="INFO">
        <appender-ref ref="JSON"/>
        <appender-ref ref="FILE"/>
    </root>
</configuration>

// 代码中的日志记录
@Service
@Slf4j
public class OrderService {
    
    public OrderResult createOrder(OrderRequest request) {
        log.info("开始创建订单", 
            kv("customerId", request.getCustomerId()),
            kv("itemCount", request.getItems().size()),
            kv("totalAmount", request.getTotalAmount()));
        
        try {
            // 业务逻辑
            Order order = processOrder(request);
            
            log.info("订单创建成功",
                kv("orderId", order.getId()),
                kv("status", order.getStatus()),
                kv("processingTime", System.currentTimeMillis() - startTime));
                
            return OrderResult.success(order);
            
        } catch (BusinessException e) {
            log.warn("订单创建失败 - 业务异常",
                kv("errorCode", e.getErrorCode()),
                kv("customerId", request.getCustomerId()),
                kv("reason", e.getMessage()));
            throw e;
            
        } catch (Exception e) {
            log.error("订单创建失败 - 系统异常",
                kv("customerId", request.getCustomerId()),
                kv("error", e.getMessage()),
                kv("stackTrace", ExceptionUtils.getStackTrace(e)));
            throw new SystemException("订单处理失败", e);
        }
    }
}

// 输出示例（JSON格式）
{
  "@timestamp": "2023-10-01T12:30:45.123+08:00",
  "level": "INFO",
  "logger": "com.example.OrderService",
  "message": "订单创建成功",
  "app": "ecommerce-platform",
  "env": "production",
  "orderId": "ORD-20231001-001",
  "status": "COMPLETED",
  "processingTime": 150
}
```

### 10.2 监控指标配置
```java
// 使用Micrometer收集指标
@Configuration
public class MetricsConfig {
    
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config()
            .commonTags("application", "order-service", "environment", "production");
    }
    
    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }
}

// 业务指标收集
@Service
public class OrderMetrics {
    
    private final MeterRegistry meterRegistry;
    private final Counter orderCreatedCounter;
    private final DistributionSummary orderAmountSummary;
    private final Timer orderProcessingTimer;
    
    public OrderMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        this.orderCreatedCounter = Counter.builder("orders.created")
            .description("创建的订单数量")
            .tag("type", "total")
            .register(meterRegistry);
            
        this.orderAmountSummary = DistributionSummary.builder("orders.amount")
            .description("订单金额分布")
            .baseUnit("CNY")
            .register(meterRegistry);
            
        this.orderProcessingTimer = Timer.builder("orders.processing.time")
            .description("订单处理时间")
            .register(meterRegistry);
    }
    
    public void recordOrderCreated(Order order) {
        orderCreatedCounter.increment();
        orderAmountSummary.record(order.getTotalAmount().doubleValue());
    }
    
    public Timer.Sample startProcessingTimer() {
        return Timer.start(meterRegistry);
    }
    
    public void stopProcessingTimer(Timer.Sample sample, Order order) {
        sample.stop(orderProcessingTimer);
    }
}

// 在业务代码中使用
@Service
public class OrderService {
    
    private final OrderMetrics metrics;
    
    public OrderResult createOrder(OrderRequest request) {
        Timer.Sample timer = metrics.startProcessingTimer();
        
        try {
            Order order = processOrder(request);
            metrics.recordOrderCreated(order);
            
            return OrderResult.success(order);
            
        } finally {
            metrics.stopProcessingTimer(timer, order);
        }
    }
}

// 健康检查端点
@Component
public class DatabaseHealthIndicator implements HealthIndicator {
    
    private final DataSource dataSource;
    
    public DatabaseHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    @Override
    public Health health() {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(1000)) {
                return Health.up()
                    .withDetail("database", "连接正常")
                    .build();
            } else {
                return Health.down()
                    .withDetail("database", "连接无效")
                    .build();
            }
        } catch (Exception e) {
            return Health.down(e)
                .withDetail("database", "连接失败")
                .build();
        }
    }
}
```