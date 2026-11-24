RESTRO RUSH - JAVA RESTAURANT MANAGEMENT SIMULATOR
=================================================

REQUIREMENTS
------------
- Java JDK 8 or later (Java 17 or Java 21/25 recommended)
- MySQL Server (v8.0 or later)
- IntelliJ IDEA, Eclipse, or terminal

HOW TO RUN IN INTELLIJ
----------------------
1. Open the RestroRush folder in IntelliJ IDEA.
2. The MySQL connector library in 'lib/mysql-connector-j-8.4.0.jar' is already configured.
3. Open src/Main.java and click Run.

HOW TO RUN FROM TERMINAL
------------------------
Open a terminal in the RestroRush folder:

- On macOS / Linux:
    javac -cp "lib/*" -d out src/model/*.java src/manager/*.java src/service/*.java src/storage/*.java src/Main.java
    java -cp "out:lib/*" Main

- On Windows:
    javac -cp "lib/*" -d out src/model/*.java src/manager/*.java src/service/*.java src/storage/*.java src/Main.java
    java -cp "out;lib/*" Main

GITHUB SETUP & MYSQL PASSWORD SAFETY FOR 4 USERS
------------------------------------------------
1. 'db.properties' is ignored by Git in .gitignore so each collaborator's password
   stays secure and is NEVER pushed to GitHub.
2. Each user copies 'db.properties.example' to 'db.properties' and puts their own local
   MySQL password in it.
3. On startup, the program automatically connects to MySQL, creates 'restrorush_db'
   and 'order_history' table if they do not exist.
4. An optional SQL script 'schema.sql' is also provided for manual setup.
5. If MySQL is unreachable, the program falls back to 'data/orders.txt' so it never crashes.

PROJECT FEATURES
----------------
- Menu display and dish quantity management
- Customer order placement and bill calculation
- High/normal priority order queue
- Four chef workers using ExecutorService
- Concurrent order preparation
- Cancellation of pending orders (dishes return to stock)
- Revenue and chef performance reports
- Completed order history saved in MySQL database (with local file backup)
- View saved order history directly from MySQL database (Option 8)
