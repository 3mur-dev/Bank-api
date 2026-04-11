# Bank API

A Spring Boot REST API for core banking operations with JWT authentication and role-based authorization.

## Features

- User registration and login (`JWT`)
- User management (`CRUD`)
- Account management (`CRUD`)
- Account transactions: deposit, withdraw, transfer
- Fraud checks on transfers (allow, deny, or pending review)
- Admin transaction review: list/filter, approve, reject
- Admin-only stats endpoint

## Tech Stack

- Java 21
- Spring Boot 3.3.2
- Spring Security + JWT (`jjwt`)
- Spring Data JPA (Hibernate)
- MySQL (dev/prod)
- H2 (test profile)
- Maven

## Prerequisites

- JDK 21
- MySQL 8+

## Quick Start

1. Clone the repository.
2. Create a local `.env` file from `.env.example`.
3. Update your database credentials and JWT secret.
4. Create the `bank_api` database in MySQL.
5. Start the app.

Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

macOS/Linux:

```bash
./mvnw spring-boot:run
```

Default URL: `http://127.0.0.1:8080`

## Environment Variables

```properties
DB_URL=jdbc:mysql://localhost:3306/bank_api?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
DB_USERNAME=root
DB_PASSWORD=replace-with-your-password
PORT=8080
SERVER_ADDRESS=127.0.0.1
JWT_SECRET=replace-with-a-long-random-secret-at-least-32-chars
JWT_EXPIRATION_MS=86400000
SPRING_PROFILES_ACTIVE=dev
```

`JWT_SECRET` should be at least 32 characters.

## Database Initialization

The API expects these roles to exist:

```sql
INSERT INTO roles(name) VALUES ('ROLE_USER');
INSERT INTO roles(name) VALUES ('ROLE_ADMIN');
```

## Profiles

- `dev`: MySQL, `ddl-auto=update`, SQL logging enabled
- `prod`: MySQL, `ddl-auto=validate`
- `test`: H2 in-memory DB (used by tests)

## Security Model

- Public routes: `GET /`, `POST /api/auth/register`, `POST /api/auth/login`
- Protected routes: any route outside `/api/auth/**` and `/`
- Admin routes: `/api/admin/**` requires `ROLE_ADMIN`

Use:

```http
Authorization: Bearer <token>
```

## API Endpoints

Auth:

- `POST /api/auth/register`
- `POST /api/auth/login`

Users:

- `GET /api/users`
- `GET /api/users/{id}`
- `PUT /api/users/{id}`
- `DELETE /api/users/{id}`

Accounts:

- `GET /api/accounts`
- `GET /api/accounts/{id}`
- `POST /api/accounts/create`
- `PUT /api/accounts/{id}`
- `DELETE /api/accounts/{id}`
- `POST /api/accounts/{id}/deposit`
- `POST /api/accounts/{id}/withdraw`
- `POST /api/accounts/{id}/transfer`

Transactions (admin):

- `GET /api/transactions?page=0&size=10&accountId=&status=&type=&fraud=`
- `POST /api/transactions/{id}/approve`
- `POST /api/transactions/{id}/reject`

Admin:

- `GET /api/admin/dashboard`
- `GET /api/admin/stats`

## Example Requests

Register:

```http
POST /api/auth/register
Content-Type: application/json

{
  "username": "omar",
  "email": "omar@example.com",
  "password": "password123"
}
```

Login:

```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "omar",
  "password": "password123"
}
```

Deposit:

```http
POST /api/accounts/1/deposit
Authorization: Bearer <token>
Content-Type: application/json

{
  "amount": 100.00
}
```

Transfer:

```http
POST /api/accounts/1/transfer
Authorization: Bearer <token>
Content-Type: application/json

{
  "receiver": 2,
  "amount": 40.00
}
```

High-value transfer behavior:

- Transfers above `5000` are flagged and stored as `PENDING`.
- Admin must approve or reject pending transactions.
- Denied transfers do not move balances.

## Running Tests

Windows:

```powershell
.\mvnw.cmd test
```

macOS/Linux:

```bash
./mvnw test
```
