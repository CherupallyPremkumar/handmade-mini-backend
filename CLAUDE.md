# CLAUDE.md — Dhanunjaiah Handlooms Backend

## What This Is

Backend API for **dhanunjaiah.com** — an ecommerce store selling handwoven Pochampally Ikat sarees. Live in production processing real payments via Razorpay.

## Tech Stack

- **Java 21** (Amazon Corretto on EC2)
- **Spring Boot 3.3** (Web, JPA, Security, Validation)
- **PostgreSQL** (Supabase, schema: `homebase_db`)
- **Razorpay** (payment gateway, redirect checkout + webhooks)
- **Cloudflare R2** (image/video storage via S3-compatible API)
- **Liquibase** (database migrations)
- **Cucumber BDD** (98 test scenarios)

## Architecture

```
src/main/java/com/pochampally/
├── config/
│   ├── SecurityConfig.java      # CORS (from env), CSRF disabled, JWT filter, HSTS
│   ├── JwtAuthFilter.java       # Reads JWT from httpOnly cookie OR Authorization header
│   ├── LoginRateLimiter.java    # 5 attempts per IP per 60 seconds
│   ├── R2Config.java            # S3Client + S3Presigner beans for Cloudflare R2
│   └── GlobalExceptionHandler.java
├── controller/
│   ├── AuthController.java      # Register/login (sets httpOnly cookie) + logout
│   ├── ProductController.java   # Public: list/filter/search. Admin: CRUD
│   ├── CartController.java      # Session-based cart (public, no auth)
│   ├── CheckoutController.java  # Create order → Razorpay redirect → payment callback
│   ├── OrderController.java     # Public: track. Auth: my orders. Admin: manage
│   ├── MediaController.java     # Presigned URL generation for direct R2 uploads
│   ├── ImageController.java     # Legacy image upload (through server)
│   ├── VideoController.java     # Legacy video upload (through server)
│   └── RazorpayWebhookController.java  # payment.captured / payment.failed
├── dto/                         # Request/response DTOs with validation
├── entity/                      # JPA entities (Product, Order, OrderItem, CartItem, User)
├── repository/                  # Spring Data JPA repositories
└── service/
    ├── OrderService.java        # PENDING_PAYMENT → PAID (stock decrement) → SHIPPED → DELIVERED
    ├── ProductService.java      # CRUD + atomic stock decrement (WHERE stock >= qty)
    ├── AuthService.java         # BCrypt + JWT generation
    ├── RazorpayService.java     # Create orders, verify signatures (HMAC-SHA256, constant-time)
    ├── ImageStorageService.java # Presigned URLs + legacy upload + delete
    ├── CartService.java         # Session cart with stock validation
    ├── VideoStorageService.java # Video upload with magic byte validation
    └── JwtService.java          # JWT generate/validate (HMAC-SHA512)
```

## Key Design Decisions

1. **PENDING_PAYMENT flow** — order created with no stock decrement. Stock only decremented when payment confirmed (markAsPaid). Expired orders auto-cancelled after 30 min by @Scheduled.
2. **Pessimistic locking** — `findByRazorpayOrderIdForUpdate` prevents double payment race condition between webhook and callback.
3. **Stock exhaustion handling** — if stock decrement fails after payment, order marked `captured_stock_exhausted` for manual refund.
4. **Presigned URLs** — images/videos uploaded directly from browser to R2. Backend only generates signed URLs and confirms uploads. Never proxies files.
5. **httpOnly cookie auth** — JWT set as `Secure; HttpOnly; SameSite=None` cookie. JwtAuthFilter reads from cookie first, falls back to Authorization header.
6. **CORS from env** — `${CORS_ORIGINS}` env var, no hardcoded domains.
7. **Per-product GST** — each product has its own `gstPct`. Order total calculated per-item, not flat rate.
8. **Idempotent payments** — markAsPaid returns existing order if already PAID. Safe to call multiple times.

## Environment Variables (ALL REQUIRED — no defaults)

| Variable | Example |
|---|---|
| `DB_URL` | `jdbc:postgresql://host:5432/postgres?currentSchema=homebase_db` |
| `DB_USERNAME` | `postgres.xxxxx` |
| `DB_PASSWORD` | `secret` |
| `JWT_SECRET` | `64-char-random-string` |
| `RAZORPAY_KEY_ID` | `rzp_test_xxx` or `rzp_live_xxx` |
| `RAZORPAY_KEY_SECRET` | `secret` |
| `RAZORPAY_WEBHOOK_SECRET` | `secret` |
| `CF_ACCOUNT_ID` | Cloudflare account ID |
| `CF_R2_ACCESS_KEY` | R2 access key |
| `CF_R2_SECRET_KEY` | R2 secret key |
| `CF_R2_BUCKET` | `dhanunjaiah-media` |
| `CF_R2_PUBLIC_DOMAIN` | `pub-xxx.r2.dev` |
| `FRONTEND_URL` | `https://dhanunjaiah.com` |
| `CORS_ORIGINS` | `https://dhanunjaiah.com,https://www.dhanunjaiah.com` |

## Build & Test

```bash
mvn clean package -DskipTests   # Build
mvn test                         # Run 98 BDD tests
java -jar target/*.jar           # Run (all env vars must be set)
```

## BDD Tests (98 scenarios)

| Feature | Scenarios | Coverage |
|---|---|---|
| Auth | 16 | Register, login, validation, rate limiting, JWT |
| Products | 17 | CRUD, filters, search, admin auth, validation |
| Cart | 13 | Add, merge, remove, stock validation, sessions |
| Checkout | 14 | Order creation, GST, shipping, stock, PENDING_PAYMENT |
| Orders | 15 | Tracking, status transitions, PII hidden, admin |
| Webhooks | 11 | Signature verification, idempotency, stock decrement |

## Branching

- `main` — production
- `dev` — development

## What NOT To Do

- Don't hardcode secrets — all from env vars
- Don't add default values for secrets in application.yml
- Don't decrement stock at order creation — only on payment confirmation
- Don't skip signature verification on payment callbacks
- Don't use `ThreadLocalRandom` for security-sensitive values — use `SecureRandom`
- Don't return internal database IDs in API responses
- Don't allow `*` in CORS origins for authenticated endpoints
