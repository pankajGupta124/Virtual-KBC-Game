KBC VIRTUAL GAME (JAVA + MYSQL)
================================

Project summary
---------------
This project is a complete Java desktop implementation of a virtual KBC-style quiz game.
It uses:
- Java Swing for the GUI
- MySQL 8.0 for persistent storage
- JDBC for database access
- FreeTTS (Java library) for question narration
- Java MIDI for background music and answer feedback

Main features
-------------
- Clean, animated Java GUI
- 15 randomly selected questions per session
- 30-second timer for every question
- Lifelines:
  1. 50/50
  2. Phone a Friend
  3. Ask the Audience
- Current score and question number always visible
- Text-to-speech narration of questions and options
- Dynamic suspense / correct / wrong audio feedback
- MySQL-backed question bank and user interaction logging
- 50 technical questions across multiple categories
- Scalable database schema for future question expansion
- Error handling for DB connection failures and missing data

Required software
-----------------
1. JDK 17 or later (JDK 21 also works)
2. Apache Maven 3.8+ (recommended build tool)
3. MySQL Server 8.0
4. MySQL Command Line Client 8.0
5. Windows audio output enabled for best experience

Files included
--------------
1. pom.xml
2. README.txt
3. sql\kbc_game.sql
4. src\main\java\KBCGame.java
5. src\main\java\Question.java
6. src\main\java\Option.java
7. src\main\java\UserInteraction.java
8. src\main\java\DatabaseManager.java
9. src\main\java\GameGUI.java
10. src\main\resources\database.properties
11. src\main\resources\game.properties

Important database note
-----------------------
core tables are:
- questions
- options
- user_interactions

This project also includes a very small support table:
- users

Reason:
The user_interactions table contains user_id and this is implemented as a proper foreign key.
The users table is therefore required to keep the schema valid and scalable.

PROJECT STRUCTURE
-----------------
kbc-virtual-game/
|-- pom.xml
|-- README.txt
|-- sql/
|   `-- kbc_game.sql
`-- src/
    `-- main/
        |-- java/
        |   |-- KBCGame.java
        |   |-- DatabaseManager.java
        |   |-- GameGUI.java
        |   |-- Option.java
        |   |-- Question.java
        |   `-- UserInteraction.java
        `-- resources/
            |-- database.properties
            `-- game.properties

SIMPLE ARCHITECTURE DIAGRAM
---------------------------
+-------------------+        JDBC        +----------------------+
|   Swing GUI       | <----------------> |      MySQL 8.0       |
|   GameGUI         |                    | kbc_game database    |
+---------+---------+                    +----------+-----------+
          |                                           |
          | uses                                      |
          v                                           v
+-------------------+                    +----------------------+
| DatabaseManager   |                    | questions            |
| Loads config      |                    | options              |
| Fetches questions |                    | users                |
| Saves interactions|                    | user_interactions    |
+-------------------+                    +----------------------+
          |
          v
+-------------------+
|  Audio + TTS      |
| FreeTTS + MIDI    |
+-------------------+

============================================================
STEP-BY-STEP SETUP GUIDE USING MYSQL COMMAND LINE CLIENT 8.0
============================================================

STEP 1 - Install Java
---------------------
Install JDK 17 or later.
Check installation:

    java -version
    javac -version

If both commands work, Java is ready.

STEP 2 - Install Maven
----------------------
Check installation:

    mvn -version

If Maven is not installed, install it and reopen terminal.

STEP 3 - Install MySQL Server and MySQL Command Line Client 8.0
---------------------------------------------------------------
Make sure you know:
- MySQL username (commonly root)
- MySQL password
- Port (commonly 3306)

STEP 4 - Open MySQL Command Line Client 8.0
-------------------------------------------
Login using your MySQL account.
Example:

    mysql -u root -p

Enter your password when asked.

STEP 5 - Create the database manually from your side
----------------------------------------------------
You asked to create the database and tables yourself. Use these commands.

Option A - simplest and recommended: run the complete SQL file
--------------------------------------------------------------
Inside MySQL client, run:

    SOURCE C:/full/path/to/kbc-virtual-game/sql/kbc_game.sql;

Examples:

    SOURCE C:/Users/YourName/Desktop/kbc-virtual-game/sql/kbc_game.sql;
    SOURCE D:/Projects/kbc-virtual-game/sql/kbc_game.sql;

Important:
- Use forward slashes in MySQL SOURCE command.
- If the path contains spaces, wrap it in quotes only if your MySQL setup supports it.
  If not, move the folder to a simple path like C:/kbc-virtual-game/

Option B - fully manual database creation using command line client
-------------------------------------------------------------------
If you want to create database and tables step by step by yourself, run the following one by one.

1) Create database:

    DROP DATABASE IF EXISTS kbc_game;
    CREATE DATABASE kbc_game CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    USE kbc_game;

2) Create users table:

    CREATE TABLE users (
        user_id INT PRIMARY KEY AUTO_INCREMENT,
        user_name VARCHAR(100) NOT NULL UNIQUE,
        created_at DATETIME NOT NULL
    ) ENGINE=InnoDB;

3) Create questions table:

    CREATE TABLE questions (
        question_id INT PRIMARY KEY AUTO_INCREMENT,
        question_text VARCHAR(500) NOT NULL,
        category VARCHAR(100) NOT NULL
    ) ENGINE=InnoDB;

4) Create options table:

    CREATE TABLE options (
        option_id INT PRIMARY KEY AUTO_INCREMENT,
        question_id INT NOT NULL,
        option_label CHAR(1) NOT NULL,
        option_text VARCHAR(300) NOT NULL,
        is_correct BOOLEAN NOT NULL DEFAULT FALSE,
        CONSTRAINT fk_options_question
            FOREIGN KEY (question_id) REFERENCES questions(question_id)
            ON DELETE CASCADE,
        CONSTRAINT uq_question_option_label UNIQUE (question_id, option_label)
    ) ENGINE=InnoDB;

5) Create user_interactions table:

    CREATE TABLE user_interactions (
        interaction_id INT PRIMARY KEY AUTO_INCREMENT,
        user_id INT NOT NULL,
        session_id VARCHAR(64) NOT NULL,
        question_id INT NOT NULL,
        selected_option_id INT NULL,
        score INT NOT NULL DEFAULT 0,
        lifelines_used VARCHAR(255) NOT NULL,
        is_correct BOOLEAN NOT NULL,
        question_number INT NOT NULL,
        time_taken_seconds INT NOT NULL,
        answered_at DATETIME NOT NULL,
        CONSTRAINT fk_interactions_user FOREIGN KEY (user_id) REFERENCES users(user_id),
        CONSTRAINT fk_interactions_question FOREIGN KEY (question_id) REFERENCES questions(question_id),
        CONSTRAINT fk_interactions_option FOREIGN KEY (selected_option_id) REFERENCES options(option_id)
    ) ENGINE=InnoDB;

6) Create indexes:

    CREATE INDEX idx_questions_category ON questions(category);
    CREATE INDEX idx_interactions_session_id ON user_interactions(session_id);
    CREATE INDEX idx_interactions_user_id ON user_interactions(user_id);
    CREATE INDEX idx_options_question_id ON options(question_id);

7) Insert question and option data:
Use the complete SQL file to load the 50 questions and 200 options:

    SOURCE C:/full/path/to/kbc-virtual-game/sql/kbc_game.sql;

Note:
The SQL file already contains everything in correct order, so Option A is safer.

STEP 6 - Verify that data was imported correctly
------------------------------------------------
Inside MySQL client, run:

    USE kbc_game;
    SELECT COUNT(*) AS total_questions FROM questions;
    SELECT COUNT(*) AS total_options FROM options;
    SELECT COUNT(*) AS total_users FROM users;
    SELECT COUNT(*) AS total_interactions FROM user_interactions;

Expected initial result:
- total_questions = 50
- total_options = 200
- total_users = 0 (before first game launch)
- total_interactions = 0 (before first game launch)

You can also preview sample data:

    SELECT * FROM questions LIMIT 5;
    SELECT * FROM options WHERE question_id = 1;

STEP 7 - Update database configuration in the Java project
----------------------------------------------------------
Open this file:

    src/main/resources/database.properties

Set your values:

    db.url=jdbc:mysql://localhost:3306/kbc_game?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
    db.user=root
    db.password=your_mysql_password

Examples:
- If your MySQL password is 1234, then:

    db.password=1234

- If MySQL runs on a different port, change 3306 in db.url.

STEP 8 - Build the project
--------------------------
Open terminal / command prompt in the project root folder.
Example:

    cd C:\path\to\kbc-virtual-game

Build with Maven:

    mvn clean package

If build succeeds, Maven creates a runnable JAR in the target folder.

STEP 9 - Run the game
---------------------
Run this command from the project root folder:

    java -jar target\kbc-virtual-game-1.0.0.jar

Alternative from Maven:

    mvn exec:java -Dexec.mainClass=KBCGame

Recommended method:
Use the JAR command above because it runs the packaged build with dependencies.

STEP 10 - Play the game
-----------------------
1. Launch the application.
2. Click "Start / Restart Game".
3. Enter player name.
4. The app creates or loads the user from MySQL.
5. 15 random questions are loaded.
6. Use lifelines when needed.
7. Each answer is recorded in user_interactions.
8. Final score is shown in a popup at the end.

==================================================
HOW TO CHECK STORED USER INTERACTIONS IN MYSQL 8.0
==================================================

After playing at least one session, run:

    USE kbc_game;

See all interaction rows:

    SELECT * FROM user_interactions;

See latest interactions first:

    SELECT *
    FROM user_interactions
    ORDER BY interaction_id DESC;

See a player's session history:

    SELECT u.user_name,
           ui.session_id,
           ui.question_number,
           q.question_text,
           ui.score,
           ui.lifelines_used,
           ui.is_correct,
           ui.time_taken_seconds,
           ui.answered_at
    FROM user_interactions ui
    JOIN users u ON ui.user_id = u.user_id
    JOIN questions q ON ui.question_id = q.question_id
    ORDER BY ui.interaction_id DESC;

====================================================
HOW THE GAME MATCHES YOUR REQUESTED REQUIREMENTS
====================================================

1. Clean and intuitive GUI interface
- Implemented using Java Swing with themed cards, side prize ladder, status panels, and animated background.

2. 30-second timer
- Implemented with Swing Timer.
- Progress bar and time label update every second.

3. Lifelines
- 50/50 removes two incorrect answers.
- Phone a Friend gives a likely answer.
- Ask the Audience shows simulated audience percentages.

4. Audio feedback
- Java MIDI is used to play:
  - suspense loop
  - correct-answer stinger
  - wrong-answer stinger

5. TTS
- FreeTTS narrates the question and options.
- If TTS is unavailable on a machine, the game continues without crashing.

6. Database storage
- Questions and options are normalized into separate tables.
- User answers, score, timing, correctness, and lifelines are stored in user_interactions.

7. Random selection of 15 questions
- The project fetches 15 random questions from the questions table for each session.

8. Scalability
- To add more questions later, simply insert new rows into questions and options.
- No Java code changes are needed if the schema stays the same.

9. Error handling
- Failed DB connection shows a clear error dialog.
- Missing or insufficient question data is validated before starting.
- TTS and audio failures are handled gracefully.

10. Performance
- Lightweight Swing rendering.
- JDBC queries are simple and indexed.
- Audio and TTS are handled asynchronously so the UI remains responsive.

ASCII SCREEN FLOW
-----------------
+---------------------------------------------------------------+
| Header: Player | Score | Question No | Category | Timer | Msg |
+---------------------------------------------------------------+
|                       Question Panel                          |
|      Big readable question text with timer progress bar       |
|                                                               |
|  [A] Option 1              [B] Option 2                       |
|  [C] Option 3              [D] Option 4                       |
+-------------------------------------------+-------------------+
| Lifelines                                  | Prize Ladder      |
| [50/50]                                    | Q15 ₹10000000     |
| [Phone a Friend]                           | ...               |
| [Ask the Audience]                         | Q1  ₹1000         |
+-------------------------------------------+-------------------+
| Start / Restart Game + helper text                            |
+---------------------------------------------------------------+

NOTES FOR WINDOWS USERS
-----------------------
1. If Java runs but audio is muted, check system volume and sound device.
2. If MySQL connection fails, verify username, password, port, and database.properties.
3. If MySQL SOURCE command fails because of path issues, move the project folder to C:/kbc-virtual-game and try again.
4. If firewall or antivirus blocks Java audio, allow Java to access audio devices.

HOW TO ADD MORE QUESTIONS LATER
-------------------------------
1. Open MySQL Command Line Client.
2. Run:

    USE kbc_game;

3. Insert a new question:

    INSERT INTO questions (question_text, category)
    VALUES ('Your new question here', 'Your category');

4. Check the new question_id:

    SELECT * FROM questions ORDER BY question_id DESC LIMIT 1;

5. Insert four options using that question_id:

    INSERT INTO options (question_id, option_label, option_text, is_correct) VALUES
    (NEW_ID, 'A', 'Option A text', FALSE),
    (NEW_ID, 'B', 'Option B text', TRUE),
    (NEW_ID, 'C', 'Option C text', FALSE),
    (NEW_ID, 'D', 'Option D text', FALSE);

TROUBLESHOOTING
---------------
Problem: "Database connection failed"
Solution:
- Check MySQL server is running.
- Confirm database.properties values.
- Confirm kbc_game exists.
- Confirm the SQL file was imported successfully.

Problem: "No suitable driver" or JDBC errors
Solution:
- Build the project with Maven so mysql-connector-j is included.
- Run the generated JAR from target.

Problem: TTS does not speak
Solution:
- The game still works without TTS.
- Rebuild with Maven to ensure FreeTTS dependency is present.
- Check that the system audio device is available.

Problem: Build fails
Solution:
- Run:

    mvn -version
    java -version

- Make sure JDK is installed, not only JRE.

TESTING CHECKLIST
-----------------
The project was prepared to satisfy the following checks:
- Compiles as a Maven project
- Loads properties from resources
- Uses MySQL through JDBC
- Uses normalized question + option schema
- Stores session interactions
- Runs as a desktop GUI application
- Supports timer, lifelines, score display, and dynamic feedback

END OF README
