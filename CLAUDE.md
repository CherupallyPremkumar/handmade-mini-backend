# CLAUDE.md — Dhanunjaiah Handlooms Backend

## What This Is

Backend API for **dhanunjaiah.com** — an ecommerce store selling handwoven Pochampally Ikat sarees. Live in production processing real payments via Razorpay.

## Tech Stack

- **Java 21** (Amazon Corretto on EC2)
- **Spring Boot 3.3** (Web, JPA, Security, Validation)
- **PostgreSQL** (Supabase, schema: `homebase_db`)
- **Razorpay** (payment gateway, redirect checkout + webhooks)
- **Cloudflare R2** (image/video storage via S3-compatible API)
- **Email**: pluggable provider — Resend (default) or AWS SES, switched via `EMAIL_PROVIDER`
- **Redis** (optional — rate limiting / caching; falls back to in-memory when unset)
- **Liquibase** (database migrations — 17 changelogs)
- **Cucumber BDD** (test scenarios across auth, products, cart, checkout, orders, webhooks, coupons, reviews, wishlist)

## Architecture

```
src/main/java/com/pochampally/
├── config/
│   ├── SecurityConfig.java      # CORS (from env), CSRF disabled, JWT filter, HSTS
│   ├── JwtAuthFilter.java       # Reads JWT from httpOnly cookie OR Authorization header
│   ├── RateLimiter.java         # Per-IP sliding-window limiter (login + payment verify)
│   ├── CacheConfig.java         # Cache names/beans for products + product lists
│   ├── R2Config.java            # S3Client + S3Presigner beans for Cloudflare R2
│   └── GlobalExceptionHandler.java
├── controller/
│   ├── AuthController.java      # Register/login (sets httpOnly cookie), logout, email verify, password reset
│   ├── ProductController.java   # Public: list/filter/search/related. Admin: CRUD
│   ├── CartController.java      # Session-based cart (public, no auth)
│   ├── CheckoutController.java  # Create order → Razorpay → verify-payment / payment-callback
│   ├── OrderController.java     # Public: track. Auth: my orders. Admin: manage + status
│   ├── AddressController.java   # Saved shipping addresses (auth)
│   ├── WishlistController.java  # Add/remove/check wishlist (auth)
│   ├── ReviewController.java    # Product reviews (verified-purchase gated) + admin moderation
│   ├── CouponController.java    # Coupon validation/apply + admin CRUD
│   ├── CmsController.java       # Banners + categories (public read, admin write)
│   ├── SettingsController.java  # App settings (shipping, GST, email toggles) — admin
│   ├── MediaController.java     # Presigned URL generation for direct R2 uploads
│   ├── ImageController.java     # Legacy image upload (through server)
│   ├── VideoController.java     # Legacy video upload (through server)
│   └── RazorpayWebhookController.java  # payment.captured / payment.failed
├── dto/                         # Request/response DTOs with validation
├── entity/                      # Product, Order, OrderItem, CartItem, User, Address,
│                                #   Wishlist, Review, Coupon, CouponUsage, Category, Banner, AppSetting
├── repository/                  # Spring Data JPA repositories
└── service/
    ├── OrderService.java        # PENDING_PAYMENT → PAID (stock decrement) → SHIPPED → DELIVERED; expiry sweep
    ├── ProductService.java      # CRUD + atomic stock decrement (WHERE stock >= qty) + caching
    ├── AuthService.java         # BCrypt + JWT generation + email verification + password reset
    ├── RazorpayService.java     # Create orders, verify signatures (HMAC-SHA256, constant-time), refunds
    ├── CartService.java         # Session cart with stock validation
    ├── AddressService.java      # Saved-address CRUD, ownership-scoped
    ├── ReviewService.java       # Reviews, verified-purchase check, moderation
    ├── CouponService.java       # Coupon validation, per-user usage limits
    ├── SettingsService.java     # Typed app-settings accessor (getInt/getLong/get)
    ├── AnalyticsService.java    # Admin dashboard stats (revenue, order counts)
    ├── AbandonedCartService.java# Scheduled abandoned-cart reminder emails
    ├── InvoiceService.java      # Order invoice generation
    ├── EmailService.java        # Order/shipping/verification emails (delegates to provider)
    ├── email/EmailProvider.java # Interface → ResendProvider / SesProvider
    ├── ImageStorageService.java # Presigned URLs + legacy upload + delete
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

### Optional / feature env vars

| Variable | Purpose | Default |
|---|---|---|
| `PORT` | HTTP port | `8090` |
| `EMAIL_PROVIDER` | `resend` or `ses` | `resend` |
| `RESEND_API_KEY` / `RESEND_FROM_EMAIL` | Resend email | empty (emails skipped) |
| `SES_ACCESS_KEY` / `SES_SECRET_KEY` / `SES_REGION` / `SES_FROM_EMAIL` | AWS SES email | empty / `ap-south-1` |
| `REDIS_URL` / `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Redis (optional) | in-memory fallback |
| `LIQUIBASE_CONTEXTS` | Liquibase contexts (e.g. `dev` seeds sample data) | `dev` |

> Email is optional: if the provider isn't configured the app logs `… not configured — skipping` and continues. Order processing never blocks on email.

## Build & Test

```bash
mvn clean package -DskipTests   # Build
mvn test                         # Run 98 BDD tests
java -jar target/*.jar           # Run (all env vars must be set)
```

## BDD Tests (Cucumber — `mvn test`, runs green against H2/Testcontainers)

Feature files live in `src/test/resources/features/`. Coverage:

| Feature | Coverage |
|---|---|
| Auth | Register, login, validation, rate limiting, JWT, email verification, password reset |
| Products | CRUD, filters, search, related, admin auth, validation |
| Cart | Add, merge, remove, stock validation, sessions |
| Checkout | Order creation, GST, shipping, stock, PENDING_PAYMENT |
| Orders | Tracking, status transitions, PII hidden, admin |
| Webhooks | Signature verification, idempotency, stock decrement |
| Coupons | Validation, discount calc, per-user usage limits |
| Reviews | Verified-purchase gating, moderation |
| Wishlist | Add/remove/check, auth enforcement |

> The original docs claimed exactly "98 scenarios"; the suite has grown since. Run `mvn test` for the current count — the last local run passed with exit code 0.

## Branching & Deployment

- `main` — **PRODUCTION ONLY.** Never push directly. Only merge from `dev` when fully tested and ready for production. Real users are making real payments (Razorpay) — a bad deploy breaks revenue.
- `dev` — **All development happens here.** Push freely. Auto-deploys to dev instances for testing.
- **Do NOT create feature branches.** Work directly on `dev`. If you accidentally create one, merge it to `dev` immediately and delete it.
- **Never touch `main`** unless explicitly told "merge to main" or "deploy to production."

## Infrastructure

| Environment | Service | URL / Host |
|---|---|---|
| **Production** | Frontend | dhanunjaiah.com (Vercel, auto-deploys from `main`) |
| **Production** | Backend API | api.dhanunjaiah.com (AWS EC2 Mumbai) |
| **Production** | Database | Supabase PostgreSQL (ap-south-1) |
| **Dev** | Frontend | dev.dhanunjaiah.com (Vercel preview, auto-deploys from `dev`) |
| **Dev** | Backend API | dev-api.dhanunjaiah.com (AWS EC2 Mumbai) |
| **Dev** | Database | Supabase PostgreSQL (ap-south-1, separate dev instance) |
| **Both** | Images/Videos | Cloudflare R2 (APAC) |
| **Both** | Payments | Razorpay (test mode on dev, live on prod) |

Two AWS EC2 instances in Mumbai — one for prod, one for dev. Backend deployed as a JAR.

## What NOT To Do

- Don't hardcode secrets — all from env vars
- Don't add default values for secrets in application.yml
- Don't decrement stock at order creation — only on payment confirmation
- Don't skip signature verification on payment callbacks
- Don't use `ThreadLocalRandom` for security-sensitive values — use `SecureRandom`
- Don't return internal database IDs in API responses
- Don't allow `*` in CORS origins for authenticated endpoints
