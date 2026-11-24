# RestroRush - Java Restaurant Management Simulator

A multi-threaded Java restaurant management simulation console application featuring real-time concurrent order preparation with chefs (ExecutorService), dish inventory management, priority order queues, and MySQL database persistence for completed order history.

---

## 👥 GitHub Multi-User Setup (Handling MySQL Passwords Safely)

This project is configured specifically for team collaboration on GitHub across multiple users:
- **No passwords are hardcoded in Java source files.**
- `db.properties` is added to `.gitignore` so your personal MySQL password will **never be committed to GitHub**.
- A template file `db.properties.example` is tracked by Git.

### Setup for Each Team Member:
1. **Clone the repository:**
   ```bash
   git clone <repo-url>
   cd RestroRush
   ```

2. **Configure your personal `db.properties`:**
   - Copy `db.properties.example` to `db.properties`:
     ```bash
     cp db.properties.example db.properties
     ```
   - Open `db.properties` and set your local MySQL credentials:
     ```properties
     db.url=jdbc:mysql://localhost:3306/restrorush_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
     db.user=root
     db.password=your_personal_mysql_password
     ```
   *(If you run the app without creating `db.properties`, the application will automatically generate a starter file for you).*

3. **Database & Table Initialization:**
   - **Automatic (Zero Configuration):** On startup, RestroRush automatically connects to MySQL, creates the database `restrorush_db` (if it does not already exist), and creates the `order_history` table.
   - **Manual (Optional):** If you prefer initializing via MySQL CLI or MySQL Workbench, execute:
     ```bash
     mysql -u root -p < schema.sql
     ```

4. **Resilient Fallback:**
   - If MySQL is stopped or password is misconfigured, the simulator will inform you in the console and automatically save/display completed orders from `data/orders.txt`. No orders will be lost!

---

## 🚀 How to Run

### In IntelliJ IDEA:
1. Open the `RestroRush` folder in IntelliJ IDEA.
2. The MySQL JDBC connector (`lib/mysql-connector-j-8.4.0.jar`) is already linked via `RestroRush.iml`.
3. Open `src/Main.java`.
4. Click **Run 'Main'**.

### From Terminal:

#### macOS / Linux:
```bash
# Compile with MySQL Connector jar on classpath
javac -cp "lib/*" -d out src/model/*.java src/manager/*.java src/service/*.java src/storage/*.java src/Main.java

# Run
java -cp "out:lib/*" Main
```

#### Windows:
```cmd
javac -cp "lib/*" -d out src/model/*.java src/manager/*.java src/service/*.java src/storage/*.java src/Main.java
java -cp "out;lib/*" Main
```

---

## 🗄️ Database Architecture (Order History Only)

The database integration is isolated strictly to **order history** as requested:

- **MySQL Table:** `order_history`
  - `id`: Primary key (auto-increment)
  - `order_id`: Order identifier (e.g. 101)
  - `customer_name`: Name of customer
  - `total_bill`: Total amount in Rs.
  - `status`: Order status (`COMPLETED`)
  - `items_summary`: List of items and quantities ordered
  - `chef_name`: Name of chef who prepared the order
  - `completed_at`: Timestamp when the order was finished

---

## 🌟 Key Features
- **Concurrent Kitchen Simulator:** 4 chefs running concurrently via `ExecutorService`.
- **Priority Queue:** High-priority orders are processed before normal orders.
- **Stock Management:** Dish inventory is checked and reserved on order placement, and replenished on cancellation or restock.
- **Reports:** Real-time revenue, chef performance, and popularity analytics.
- **Order History:** Persisted in MySQL database with synchronized local file backup.

