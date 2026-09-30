# Circular Campus Marketplace — Resale, Cart & Wallet

**Buy less. Reuse more. Campus smarter.**

A campus-only peer-to-peer resale marketplace built for a Web Technologies PBL.
Java Servlets + JDBC + MySQL on the back, hand-written HTML/CSS/JavaScript on the front.
No Spring, no React, no Node — and no JSP.

Application URL after deployment:

```
http://localhost:8080/campus-marketplace-final/
```

---

## 1. Project overview

Students can browse without an account, and once logged in they can list items, buy with a
campus wallet, and see every transaction.

| Area | What it demonstrates |
|---|---|
| `LoginServlet`, `LogoutServlet` | HttpSession creation and invalidation |
| `CreateAccountServlet` | form validation, duplicate checks, INSERT |
| `MarketplaceServlet` | dynamic page built from MySQL, search + filters via `PreparedStatement` |
| `ViewItemServlet` | product detail plus a `recentItems` **cookie** (7 days) |
| `CreateListingServlet` | POST-only servlet, server-side validation, INSERT |
| `AddToCart`, `Cart`, `RemoveFromCart` | guest cart, logged-in cart, cart merge on login |
| `CheckoutServlet` | full **JDBC transaction**: commit / rollback, wallet, stock, receipts |
| `TransactionHistoryServlet` | purchase and sale history joins |
| `MyListingsServlet` | seller's own listings, GET to view and POST to remove |
| `AIRecommendationServlet` | local rule-based category/keyword suggestions |

**The wallet is a demo balance**, a `DECIMAL(10,2)` column in the `students` table. Money moves
between student rows inside the database. There is no payment gateway, no top-up, and nothing in
the interface pretends otherwise.

**Smart Suggestions is not an external AI service.** `AIRecommendationServlet.java` holds a table
of word fragments (`java` → Books, `cycle` → Sports …). It reads the title you typed and returns
the matching category and keywords as JSON. That rule table is the entire "intelligence" — it runs
locally in Tomcat, offline.

---

## 2. Folder structure

```
C:\campus-marketplace-final
│
├── README.md                    this file
│
├── database
│   └── campus_marketplace.sql   schema + sample data (import this first)
│
├── lib
│   └── README-lib.txt           put mysql-connector-j-8.0.33.jar here
│
├── src
│   └── main
│       ├── java                 18 source files, no packages (default package)
│       │   ├── DBConnection.java
│       │   ├── Page.java                  shared HTML shell + escaping
│       │   ├── Icons.java                 inline SVG icon set
│       │   ├── CartUtil.java              cart identity, counting, migration
│       │   ├── PasswordUtil.java          SHA-256 hashing
│       │   ├── LoginServlet.java
│       │   ├── CreateAccountServlet.java
│       │   ├── LogoutServlet.java
│       │   ├── MarketplaceServlet.java
│       │   ├── ViewItemServlet.java
│       │   ├── CreateListingServlet.java
│       │   ├── CartServlet.java
│       │   ├── AddToCartServlet.java
│       │   ├── RemoveFromCartServlet.java
│       │   ├── CheckoutServlet.java
│       │   ├── TransactionHistoryServlet.java
│       │   ├── MyListingsServlet.java
│       │   └── AIRecommendationServlet.java
│       │
│       └── webapp
│           ├── index.html
│           ├── login.html
│           ├── create-account.html
│           ├── create-listing.html
│           ├── cart.html            forwards to the /cart servlet
│           ├── transactions.html    forwards to the /transactions servlet
│           ├── my-listings.html     forwards to the /my-listings servlet
│           ├── error.html
│           ├── style.css
│           ├── script.js
│           ├── assets\
│           └── WEB-INF
│               └── web.xml         minimal: welcome file, timeout, error pages
│
├── .vscode
│   └── settings.json
│
├── build                          created by compile.bat (classes go here)
│
└── deployment
    ├── compile.bat
    ├── deploy.bat
    └── undeploy.bat
```

`C:\campus-marketplace` and `C:\xampp\tomcat\webapps\campus-marketplace` are **not used, read or
modified** by anything in this project. The scripts deploy only to
`C:\xampp\tomcat\webapps\campus-marketplace-final`.

---

## 3. Software requirements

| Tool | Version | Notes |
|---|---|---|
| XAMPP | any recent | supplies Apache, MySQL, phpMyAdmin, Tomcat |
| Apache Tomcat | 8.5.x | `C:\xampp\tomcat`, port 8080, uses `javax.servlet.*` |
| JDK | 8 or newer | `javac` must be on the PATH |
| MySQL | from XAMPP | `localhost:3306`, user `root`, blank password |
| MySQL Connector/J | 8.0.33 | **download once**, see section 4 |
| VS Code | any | optional, Extension Pack for Java is handy |

### Things you download once

Only one file is not included:

**`mysql-connector-j-8.0.33.jar`** — get it from
<https://dev.mysql.com/downloads/connector/j/> (choose *Platform Independent*, download the ZIP,
open it, copy the jar out) and place it at:

```
C:\campus-marketplace-final\lib\mysql-connector-j-8.0.33.jar
```

`compile.bat` refuses to run without it and tells you the same thing.
`servlet-api.jar` is **not** downloaded — Tomcat already has it at
`C:\xampp\tomcat\lib\servlet-api.jar`, and it must never be copied into `WEB-INF/lib`.

---

## 4. Put the project on your machine

Copy the `campus-marketplace-final` folder so that it sits at exactly:

```
C:\campus-marketplace-final
```

Then add the jar from the previous section into `C:\campus-marketplace-final\lib`.

---

## 5. XAMPP setup

1. Open the XAMPP Control Panel.
2. Start **MySQL**.
3. Start **Apache** only if you want phpMyAdmin (you do, for the import).
4. Tomcat is started separately — see section 11.

If port 8080 is taken, change it in `C:\xampp\tomcat\conf\server.xml` and adjust the URL.

---

## 6. MySQL and phpMyAdmin: importing the database

1. Open <http://localhost/phpmyadmin/>
2. Click the **Import** tab (no need to create the database first — the script creates it).
3. Choose file: `C:\campus-marketplace-final\database\campus_marketplace.sql`
4. Scroll down, click **Go**.
5. On the left you now see **campus_marketplace** with four tables.

Command-line alternative:

```bat
cd C:\xampp\mysql\bin
mysql -u root < C:\campus-marketplace-final\database\campus_marketplace.sql
```

> The script begins with `DROP DATABASE IF EXISTS campus_marketplace;`, so re-importing resets
> everything to the sample data. That is the fastest way to get a clean demo before a review.

### Tables created

| Table | Purpose |
|---|---|
| `students` | account, SHA-256 password, `wallet_balance`, `verification_status` |
| `listings` | items for sale, `` `condition` `` in backticks (reserved word), `status`, `quantity` |
| `cart_items` | one row per cart line; guest rows use `session_id`, member rows use `student_id` |
| `transactions` | one row per purchased line: buyer, seller, listing, amount, quantity, date |

---

## 7. JDBC setup

`DBConnection.java` already contains:

```java
jdbc:mysql://localhost:3306/campus_marketplace
user "root", password ""      // XAMPP default
driver com.mysql.cj.jdbc.Driver
```

If your MySQL root account has a password, edit `PASSWORD` in `DBConnection.java` and recompile.
Nothing else in the project holds database credentials.

---

## 8. VS Code setup (optional)

Open the folder `C:\campus-marketplace-final`. `.vscode/settings.json` already points the Java
extension at `src/main/java`, the `lib` folder and `C:\xampp\tomcat\lib\servlet-api.jar`, so
`javax.servlet` imports resolve and you get autocomplete. You do not need VS Code to build —
the batch files do everything.

---

## 9. Compilation

```bat
cd C:\campus-marketplace-final\deployment
compile.bat
```

It finds `servlet-api.jar` and the MySQL jar, compiles every `.java` under `src\main\java` with
`javac --release 8` (falling back to plain `javac` on older JDKs), and writes classes to
`C:\campus-marketplace-final\build\classes`.

The sources use the **default package** (no `package` line), which keeps the compiled `.class`
files flat inside `WEB-INF/classes` — simplest possible layout for deployment.

---

## 10. Deployment

```bat
cd C:\campus-marketplace-final\deployment
deploy.bat
```

`deploy.bat` compiles first, then builds:

```
C:\xampp\tomcat\webapps\campus-marketplace-final
├── index.html, login.html, create-account.html, create-listing.html
├── cart.html, transactions.html, my-listings.html, error.html
├── style.css, script.js, assets\
└── WEB-INF
    ├── web.xml
    ├── classes\   (all .class files)
    └── lib\       (mysql-connector-j-8.0.33.jar only)
```

To remove it again: `undeploy.bat` (it asks for confirmation and only ever deletes
`campus-marketplace-final`).

---

## 11. Starting Tomcat

Either use the XAMPP Control Panel's Tomcat row, or:

```bat
C:\xampp\tomcat\bin\startup.bat
```

Stop it with `C:\xampp\tomcat\bin\shutdown.bat`. Logs are in `C:\xampp\tomcat\logs` —
`catalina.*.log` and `localhost.*.log` are where server-side messages from this project appear.

Redeploying while Tomcat runs usually works; if a class seems stuck, stop Tomcat, run
`deploy.bat`, start Tomcat.

---

## 12. Opening the application

```
http://localhost:8080/campus-marketplace-final/
```

---

## 13. Test account

| | |
|---|---|
| Name | Test Student |
| Email | `student@test.com` |
| Password | `123456` |
| Wallet | ₹1,000.00 |
| Status | Verified |

Three more sample accounts exist so listings have real sellers:
`rahul@test.com` / `rahul123`, `priya@test.com` / `priya123`, `arjun@test.com` / `arjun123`.
None of the sample listings belong to the test student, so it can buy any of them.

---

## 14. Testing the whole system

Run these in order — they cover every requirement of the project.

| # | Step | What you should see |
|---|---|---|
| 1 | Open `http://localhost:8080/campus-marketplace-final/` | landing page with the floating 3D hero |
| 2 | Click **Browse marketplace** (no login) | listings loaded from MySQL, counts match the database |
| 3 | Search `java` | only the Java textbook matches; URL is `marketplace?q=java` |
| 4 | Filter by category / condition / max price / sort | the grid narrows, filters stay selected |
| 5 | Open any item | detail page at `view-item?id=…` |
| 6 | Press F12 → Application → Cookies | a `recentItems` cookie holding listing ids |
| 7 | Open a second item | "Recently viewed" strip appears under the detail |
| 8 | **Add to cart** as a guest | button morphs to "Added ✓", navbar count rises |
| 9 | Log in as `student@test.com` | a message says your guest items moved into your account |
| 10 | Open **Cart** | the guest item is there, priced from the database |
| 11 | **Sell an item** → type "Java programming book" | Smart Suggestions offers Books + keywords |
| 12 | Publish the listing | you land on its detail page; it appears in the marketplace |
| 13 | Add someone else's item to cart, then **Pay with wallet** | receipt with transaction ids |
| 14 | Look at the navbar wallet | balance has dropped by the total |
| 15 | Open **Transactions** | the purchase is listed; the seller sees a sale |
| 16 | Open the item you bought | quantity is lower |
| 17 | Buy the last unit of something | status becomes **Sold** and it leaves the grid |
| 18 | Try to buy your own listing | it is blocked, with a link to My listings |
| 19 | **Log out** | session invalidated, back to the login page |
| 20 | Open `transactions` while logged out | you are asked to log in instead |

### Guest cart migration, in detail

This is one of the main learning objectives, so here is exactly what happens:

1. A guest adds an item. `CartUtil` writes a `cart_items` row with
   `session_id = <HttpSession id>` and `student_id = NULL`.
2. The guest logs in. `LoginServlet` remembers that session id, calls `invalidate()`
   (a fresh id after login protects against session fixation) and creates the new session.
3. `CartUtil.migrateGuestCart` merges quantities for listings already in the member's cart,
   then updates the remaining guest rows to `student_id = <the student>`.
4. The marketplace shows "*n item(s) from your guest cart moved into your account.*"

You can watch it happen in phpMyAdmin by looking at `cart_items` before and after the login.

### Checkout transaction, in detail

`CheckoutServlet` runs one JDBC transaction:

```
setAutoCommit(false)
  SELECT cart + listings ... FOR UPDATE      (lock rows, read real prices)
  validate: available, enough stock, not your own item, cart not empty
  SELECT wallet_balance ... FOR UPDATE
  if wallet < total  -> rollback and explain
  UPDATE buyer wallet  -  total
  UPDATE each seller wallet  +  line amount
  INSERT one transactions row per line
  UPDATE listings: quantity down, status 'Sold' at zero
  DELETE the purchased cart rows
commit()                                       (rollback() on any failure)
```

Prices submitted by the browser are ignored completely — every amount is read from `listings`
inside the transaction.

---

## 15. URL map

| URL | Method | Rendered by | Linkable from a page? |
|---|---|---|---|
| `/` `index.html` | GET | static | yes |
| `login.html` | GET | static | yes |
| `create-account.html` | GET | static | yes |
| `create-listing.html` | GET | static | yes — the form page, **not** the servlet |
| `marketplace` | GET | `MarketplaceServlet` | yes |
| `view-item?id=n` | GET | `ViewItemServlet` | yes |
| `cart` | GET | `CartServlet` | yes |
| `transactions` | GET | `TransactionHistoryServlet` | yes |
| `my-listings` | GET / POST | `MyListingsServlet` | yes (GET); POST removes a listing |
| `ai-suggest?title=…` | GET | `AIRecommendationServlet` | fetched by script.js |
| `login` | POST | `LoginServlet` | form only |
| `create-account` | POST | `CreateAccountServlet` | form only |
| `create-listing` | POST | `CreateListingServlet` | form only |
| `add-to-cart` | POST | `AddToCartServlet` | form only |
| `remove-from-cart` | POST | `RemoveFromCartServlet` | form only |
| `checkout` | POST | `CheckoutServlet` | form only |
| `logout` | GET or POST | `LogoutServlet` | navbar uses POST |

Every servlet URL is declared with `@WebServlet` in its own class. `web.xml` contains **no**
servlet mappings, so there is nothing to conflict with.

All links and form actions are **relative** (`login`, not `/login`), because the app lives under
the context path `/campus-marketplace-final`. A leading slash would jump to the server root and
produce a 404.

---

## 16. Troubleshooting

### 404 — "The requested resource is not available"

* Is Tomcat running? Check <http://localhost:8080/>.
* Is the URL exactly `http://localhost:8080/campus-marketplace-final/` (with the trailing `-final`)?
* Did `deploy.bat` finish with `[OK] Deployed.`? Look inside
  `C:\xampp\tomcat\webapps\campus-marketplace-final` — it should contain `index.html` and `WEB-INF`.
* A 404 on `/login` instead of `/campus-marketplace-final/login` means a form action starts with a
  slash. Every action in this project is relative; check any HTML you edited.

### 405 — "HTTP method GET is not supported by this URL"

You reached a POST-only servlet with a link or by typing the address.
`create-listing`, `add-to-cart`, `remove-from-cart` and `checkout` are POST-only.
Link to `create-listing.html` (the page with the form), not to `create-listing`.
In this project those servlets also answer GET with a polite redirect, so you should never see a
405 unless you add a new link.

### The page loads but says the database is unreachable

* MySQL is not started in XAMPP, or
* the import has not been done (`campus_marketplace` does not exist), or
* your root account has a password that `DBConnection.java` does not have.

### "No suitable driver found for jdbc:mysql://…"

`mysql-connector-j-8.0.33.jar` is missing from
`C:\xampp\tomcat\webapps\campus-marketplace-final\WEB-INF\lib`.
Put it in the project `lib` folder and run `deploy.bat` again.

### `ClassNotFoundException: com.mysql.cj.jdbc.Driver`

Same cause as above, or you are using an old connector (`com.mysql.jdbc.Driver`). Use 8.0.33.

### `java.sql.SQLSyntaxErrorException: ... near 'condition'`

`condition` is a reserved word in MySQL. Every query in this project already wraps it in
backticks: `` l.`condition` ``. If you add a query, do the same.

### `Access denied for user 'root'@'localhost'`

MySQL's root password does not match `DBConnection.java`. Either clear the password in XAMPP or
set it in `DBConnection.java` and recompile.

### `javac is not recognised`

The JDK `bin` folder is not on your PATH. Add e.g. `C:\Program Files\Java\jdk-17\bin` to the
system PATH, open a **new** terminal, and run `javac -version`.

### Compilation error mentioning `javax.servlet`

`compile.bat` could not find `servlet-api.jar`. Confirm `C:\xampp\tomcat\lib\servlet-api.jar`
exists, or copy it into the project `lib` folder.

### `IllegalAccessError` / weird class errors after deployment

`servlet-api.jar` ended up inside `WEB-INF/lib`. Delete it from there — Tomcat provides it.
`deploy.bat` also deletes it automatically.

### Changes to Java do not show up

Run `deploy.bat` again (it recompiles), then restart Tomcat.

### Changes to HTML/CSS do not show up

Run `deploy.bat`, then hard-refresh the browser with **Ctrl + F5**.

---

## 17. Security notes for the report

* Every SQL statement uses `PreparedStatement` with `?` placeholders. No user input is ever
  concatenated into SQL — including the search box and the dynamic filter builder in
  `MarketplaceServlet`, where only the placeholder text is appended and the values are bound.
* All dynamic text is escaped with `Page.esc()` before it is written into HTML, so a listing
  titled `<script>alert(1)</script>` renders as harmless text.
* Passwords are stored as SHA-256 hashes, never in plain text, never in the session, never in a
  cookie, and never echoed back to the page.
* Login answers "that email and password do not match" for both a wrong password and an unknown
  email, so the page does not reveal which emails are registered.
* Checkout re-reads prices, stock and the wallet balance from the database inside the transaction,
  so tampering with the form cannot change what you pay.
* Cart deletes and listing removals include the owner in the `WHERE` clause, so guessing an id
  does not let you touch another student's data.
* The `recentItems` cookie holds only listing ids and is marked `HttpOnly`.
* Servlets keep no mutable static state: session data lives in `HttpSession`, everything else in
  the database, and every `Connection`, `PreparedStatement` and `ResultSet` is closed by
  try-with-resources. That is what makes concurrent requests safe.

---

## 18. Honest list of what is *not* implemented

Nothing in the interface claims to do these:

* no photo upload (so no `multipart/form-data` anywhere),
* no wallet top-up or payment gateway,
* no admin panel, no email verification, no password reset,
* no editing of a published listing — the only management action is **Remove**, which sets
  `status = 'Removed'` (the row stays so past transactions still make sense),
* the counts on the homepage are `SELECT COUNT(*)` results, not invented numbers.


## 2026-09-22 merged build

This build uses the original working frontend as the UI baseline and adds the newer backend functionality.

Preserved:
- Original homepage, CSS and navigation design
- Original static pages and front-end behaviour

Added/updated:
- 2–5 product image upload with client-side preview
- Multipart listing creation
- Automated listing-quality checks
- Listing image storage and secure image serving
- Updated product detail and My Listings validation information
- Payment/order-aware transaction history
- Buyer problem reports and evidence
- Product Reports page
- Updated database schema for image/validation/payment/report fields

No admin or middleman workflow is introduced.

Important: if your existing `campus_marketplace_final` database already contains the added columns/tables, do not import `database/campus_marketplace.sql` because that script is a clean-install script and drops/recreates the database.
