const API = "ExamServlet";
let questions = [];
let answers = {};
let currentQuestion = 0;
let timeLeft = 600;
let timerInterval = null;
let submitting = false;

// STUDENT LOGIN
const loginForm = document.getElementById("loginForm");

if (loginForm) {
    loginForm.addEventListener("submit", async function(e) {
        e.preventDefault();

        const name = document.getElementById("studentName").value.trim();
        const roll = document.getElementById("rollNumber").value.trim();

        if (!name || !roll) {
            document.getElementById("loginMessage").textContent =
                "Please enter all details.";
            return;
        }

        const data = new URLSearchParams();
        data.append("action", "login");
        data.append("name", name);
        data.append("roll", roll);

        try {
            const response = await fetch(API, {
                method: "POST",
                body: data
            });

            const result = await response.json();

            if (!response.ok) throw new Error(result.error);

            window.location.href = "exam.html";

        } catch (error) {
            document.getElementById("loginMessage").textContent =
                error.message || "Login failed. Check the server.";
        }
    });
}


// EXAMINATION PAGE
if (document.getElementById("questionText")) {
    startExam();
}

async function startExam() {
    try {
        const response = await fetch(API + "?action=questions");

        if (response.status === 401) {
            window.location.href = "login.html";
            return;
        }

        const data = await response.json();

        if (!response.ok) throw new Error(data.error);

        questions = data.questions;
        timeLeft = data.remainingSeconds;

        document.getElementById("studentDetails").textContent =
            data.name + " (" + data.roll + ")";

        displayQuestion();
        updateTimer();

        timerInterval = setInterval(function() {
            timeLeft--;
            updateTimer();

            if (timeLeft <= 0) {
                submitExam(true);
            }
        }, 1000);

    } catch (error) {
        document.getElementById("questionText").textContent =
            error.message || "Unable to load questions.";
    }
}


// DISPLAY QUESTION
function displayQuestion() {
    if (!questions.length) return;

    const question = questions[currentQuestion];

    document.getElementById("questionText").textContent =
        (currentQuestion + 1) + ". " + question.text;

    document.getElementById("questionNumber").textContent =
        "Question " + (currentQuestion + 1) +
        " of " + questions.length;

    const container = document.getElementById("optionsContainer");
    container.innerHTML = "";

    question.options.forEach(function(option, index) {

        const label = document.createElement("label");
        label.className = "option";

        const radio = document.createElement("input");
        radio.type = "radio";
        radio.name = "answer";
        radio.value = index;

        if (answers[question.id] === index) {
            radio.checked = true;
            label.classList.add("selected");
        }

        radio.addEventListener("change", function() {
            answers[question.id] = index;
            displayQuestion();
        });

        const text = document.createElement("span");
        text.textContent =
            String.fromCharCode(65 + index) + ". " + option;

        label.appendChild(radio);
        label.appendChild(text);

        container.appendChild(label);
    });

    document.getElementById("previousBtn").disabled =
        currentQuestion === 0;

    document.getElementById("nextBtn").textContent =
        currentQuestion === questions.length - 1 ?
        "Finish Review" : "Next";

    updateProgress();
}


// QUESTION NAVIGATION
const previousBtn = document.getElementById("previousBtn");

if (previousBtn) {
    previousBtn.addEventListener("click", function() {
        if (currentQuestion > 0) {
            currentQuestion--;
            displayQuestion();
        }
    });
}

const nextBtn = document.getElementById("nextBtn");

if (nextBtn) {
    nextBtn.addEventListener("click", function() {
        if (currentQuestion < questions.length - 1) {
            currentQuestion++;
            displayQuestion();
        } else {
            const unanswered =
                questions.length - Object.keys(answers).length;

            if (confirm("Review completed. " + unanswered +
                " question(s) are unanswered. Submit examination?")) {
                submitExam(false);
            }
        }
    });
}


// PROGRESS
function updateProgress() {
    const count = Object.keys(answers).length;

    document.getElementById("answeredCount").textContent =
        count + " Answered";

    document.getElementById("progress").style.width =
        (count / questions.length * 100) + "%";
}


// COUNTDOWN TIMER
function updateTimer() {
    const minutes = Math.floor(timeLeft / 60);
    const seconds = timeLeft % 60;

    document.getElementById("timer").textContent =
        String(minutes).padStart(2, "0") + ":" +
        String(seconds).padStart(2, "0");
}


// SUBMIT EXAMINATION
const submitBtn = document.getElementById("submitBtn");

if (submitBtn) {
    submitBtn.addEventListener("click", function() {
        if (confirm("Are you sure you want to submit the examination?")) {
            submitExam(false);
        }
    });
}

async function submitExam(autoSubmit) {
    if (submitting) return;

    submitting = true;
    clearInterval(timerInterval);

    const encodedAnswers = Object.entries(answers)
        .map(([id, index]) => id + ":" + index)
        .join(",");

    const data = new URLSearchParams();
    data.append("action", "submit");
    data.append("answers", encodedAnswers);
    data.append("autoSubmit", autoSubmit ? "true" : "false");

    try {
        const response = await fetch(API, {
            method: "POST",
            body: data
        });

        const result = await response.json();

        if (!response.ok) throw new Error(result.error);

        sessionStorage.setItem("examResult", JSON.stringify(result));

        window.location.href = "result.html";

    } catch (error) {
        submitting = false;

        alert(error.message || "Submission failed. Please try again.");

        if (timeLeft > 0) {
            timerInterval = setInterval(function() {
                timeLeft--;
                updateTimer();

                if (timeLeft <= 0) submitExam(true);
            }, 1000);
        }
    }
}


// RESULT PAGE
if (document.getElementById("score")) {
    displayResult();
}

function displayResult() {
    const result = sessionStorage.getItem("examResult");

    if (!result) {
        document.querySelector(".result-container").innerHTML =
            "<h1>Result not available</h1>" +
            '<a href="login.html" class="primary-btn">Login</a>';
        return;
    }

    const data = JSON.parse(result);

    document.getElementById("resultName").textContent = data.name;
    document.getElementById("resultRoll").textContent = data.roll;

    document.getElementById("score").textContent =
        data.correct + " / " + data.total;

    document.getElementById("percentage").textContent =
        data.percentage + "%";

    document.getElementById("correct").textContent = data.correct;
    document.getElementById("wrong").textContent = data.wrong;
    document.getElementById("unanswered").textContent = data.unanswered;

    document.getElementById("status").textContent =
        data.passed ? "PASS - Congratulations!" : "FAIL - Keep Practising!";

    document.getElementById("status").style.color =
        data.passed ? "green" : "red";
}