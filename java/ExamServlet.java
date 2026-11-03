import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@WebServlet("/ExamServlet")
public class ExamServlet extends HttpServlet {

    private static final int DURATION = 600;
    private static final int PASS_MARKS = 5;

    private static final String[][] QUESTIONS = {
        {
            "Which language is used to create web page structure?",
            "CSS", "HTML", "Java", "Python"
        },
        {
            "Which language is used to style web pages?",
            "HTML", "CSS", "SQL", "C"
        },
        {
            "Which keyword is used to create a class in Java?",
            "function", "define", "class", "new"
        },
        {
            "Which method is the entry point of a Java program?",
            "start()", "main()", "run()", "init()"
        },
        {
            "Which HTML tag creates a hyperlink?",
            "<link>", "<href>", "<a>", "<p>"
        },
        {
            "Which keyword declares a block-scoped variable in JavaScript?",
            "var", "let", "int", "define"
        },
        {
            "What does CSS stand for?",
            "Computer Style Sheets",
            "Creative Style Syntax",
            "Cascading Style Sheets",
            "Colorful Style Sheets"
        },
        {
            "Which protocol is commonly used to transfer web pages?",
            "HTTP", "FTP", "SMTP", "SSH"
        },
        {
            "Which object represents a client request in a Java Servlet?",
            "HttpServletResponse",
            "HttpSession",
            "HttpServletRequest",
            "ServletConfig"
        },
        {
            "Which symbol ends most Java statements?",
            ".", ",", ":", ";"
        }
    };

    // Correct option indexes: A=0, B=1, C=2, D=3
    private static final int[] ANSWERS = {
        1, 1, 2, 1, 2, 1, 2, 0, 2, 3
    };


    // HANDLE GET REQUESTS
    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String action = request.getParameter("action");

        if ("questions".equals(action)) {
            sendQuestions(request, response);
        } else {
            response.sendError(400, "Invalid action");
        }
    }


    // HANDLE POST REQUESTS
    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String action = request.getParameter("action");

        if ("login".equals(action)) {
            studentLogin(request, response);
        }

        else if ("submit".equals(action)) {
            submitExam(request, response);
        }

        else {
            response.setStatus(400);
            response.getWriter().write(
                "{\"error\":\"Invalid action\"}"
            );
        }
    }


    // STUDENT LOGIN
    private void studentLogin(
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {

        String name = request.getParameter("name");
        String roll = request.getParameter("roll");

        if (name == null || roll == null ||
            name.trim().isEmpty() || roll.trim().isEmpty() ||
            name.trim().length() > 60 || roll.trim().length() > 30) {

            response.setStatus(400);
            response.getWriter().write(
                "{\"error\":\"Please enter valid student details\"}"
            );
            return;
        }

        HttpSession oldSession = request.getSession(false);

        if (oldSession != null) {
            oldSession.invalidate();
        }

        HttpSession session = request.getSession(true);

        session.setAttribute("studentName", name.trim());
        session.setAttribute("rollNumber", roll.trim());
        session.setAttribute("startTime", System.currentTimeMillis());

        session.setMaxInactiveInterval(1800);

        response.getWriter().write(
            "{\"success\":true}"
        );
    }


    // SEND QUESTIONS TO STUDENT
    private void sendQuestions(
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {

        HttpSession session = request.getSession(false);

        if (!validSession(session)) {
            response.setStatus(401);
            response.getWriter().write(
                "{\"error\":\"Please login first\"}"
            );
            return;
        }

        if (session.getAttribute("result") != null) {
            response.setStatus(409);
            response.getWriter().write(
                "{\"error\":\"Examination already submitted\"}"
            );
            return;
        }

        long startTime = (Long) session.getAttribute("startTime");

        long elapsed = (System.currentTimeMillis() - startTime) / 1000;

        long remaining = Math.max(0, DURATION - elapsed);

        StringBuilder json = new StringBuilder();

        json.append("{");
        json.append("\"name\":").append(quote(
            (String) session.getAttribute("studentName")
        )).append(",");

        json.append("\"roll\":").append(quote(
            (String) session.getAttribute("rollNumber")
        )).append(",");

        json.append("\"remainingSeconds\":").append(remaining).append(",");
        json.append("\"questions\":[");

        for (int i = 0; i < QUESTIONS.length; i++) {

            if (i > 0) json.append(",");

            json.append("{");
            json.append("\"id\":").append(i + 1).append(",");
            json.append("\"text\":").append(quote(QUESTIONS[i][0])).append(",");
            json.append("\"options\":[");

            for (int j = 1; j <= 4; j++) {

                if (j > 1) json.append(",");

                json.append(quote(QUESTIONS[i][j]));
            }

            json.append("]}");
        }

        json.append("]}");

        response.getWriter().write(json.toString());
    }


    // AUTOMATIC RESULT CALCULATION
    private void submitExam(
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {

        HttpSession session = request.getSession(false);

        if (!validSession(session)) {
            response.setStatus(401);
            response.getWriter().write(
                "{\"error\":\"Please login again\"}"
            );
            return;
        }

        // Prevent duplicate submissions
        String previousResult = (String) session.getAttribute("result");

        if (previousResult != null) {
            response.getWriter().write(previousResult);
            return;
        }

        long startTime = (Long) session.getAttribute("startTime");

        long elapsed = (System.currentTimeMillis() - startTime) / 1000;

        boolean timedOut = elapsed >= DURATION;

        // Validate examination time on the server
        if (!timedOut && elapsed < 1) {
            response.setStatus(400);
            response.getWriter().write(
                "{\"error\":\"Examination cannot be submitted yet\"}"
            );
            return;
        }

        String submitted = request.getParameter("answers");

        Map<Integer, Integer> studentAnswers = new HashMap<>();

        if (submitted != null && submitted.length() <= 5000) {

            String[] pairs = submitted.split(",");

            for (String pair : pairs) {

                String[] values = pair.split(":");

                if (values.length != 2) continue;

                try {
                    int questionId = Integer.parseInt(values[0]);
                    int option = Integer.parseInt(values[1]);

                    if (questionId >= 1 &&
                        questionId <= QUESTIONS.length &&
                        option >= 0 && option < 4) {

                        studentAnswers.put(questionId, option);
                    }

                } catch (NumberFormatException ignored) {
                    // Ignore invalid answer entries
                }
            }
        }

        int correct = 0;
        int attempted = studentAnswers.size();

        for (int i = 0; i < ANSWERS.length; i++) {

            Integer selected = studentAnswers.get(i + 1);

            if (selected != null && selected == ANSWERS[i]) {
                correct++;
            }
        }

        int total = QUESTIONS.length;
        int wrong = attempted - correct;
        int unanswered = total - attempted;

        double percentage = (correct * 100.0) / total;

        boolean passed = correct >= PASS_MARKS;

        String result =
            "{"
            + "\"name\":" + quote((String) session.getAttribute("studentName")) + ","
            + "\"roll\":" + quote((String) session.getAttribute("rollNumber")) + ","
            + "\"total\":" + total + ","
            + "\"correct\":" + correct + ","
            + "\"wrong\":" + wrong + ","
            + "\"unanswered\":" + unanswered + ","
            + "\"percentage\":" + String.format(
                java.util.Locale.US, "%.2f", percentage
            ) + ","
            + "\"passed\":" + passed + ","
            + "\"timedOut\":" + timedOut
            + "}";

        session.setAttribute("result", result);

        response.getWriter().write(result);
    }


    // SESSION VALIDATION
    private boolean validSession(HttpSession session) {

        return session != null &&
            session.getAttribute("studentName") != null &&
            session.getAttribute("rollNumber") != null &&
            session.getAttribute("startTime") != null;
    }


    // JSON STRING ESCAPING
    private String quote(String value) {

        if (value == null) return "null";

        StringBuilder result = new StringBuilder("\"");

        for (char c : value.toCharArray()) {

            switch (c) {

                case '"':
                    result.append("\\\"");
                    break;

                case '\\':
                    result.append("\\\\");
                    break;

                case '\n':
                    result.append("\\n");
                    break;

                case '\r':
                    result.append("\\r");
                    break;

                case '\t':
                    result.append("\\t");
                    break;

                default:
                    if (c < 32) {
                        result.append(String.format("\\u%04x", (int)c));
                    } else {
                        result.append(c);
                    }
            }
        }

        return result.append("\"").toString();
    }
}