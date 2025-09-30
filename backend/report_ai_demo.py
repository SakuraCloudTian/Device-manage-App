from flask import Flask, request, jsonify
import mysql.connector
import google.generativeai as genai
import datetime
from flask_cors import CORS
app = Flask(__name__)
CORS(app, resources={r"/": {"origins": "*"}})
# === Database config ===
db_config = {
    'user': 'yinkuns7',
    'password': 'abc123',
    'host': '192.168.10.8',   # Database host
    'database': 'device'
}

# === Configure Google Gemini API ===
genai.configure(api_key="YOUR_API_KEY")   # TODO: Replace with your actual API KEY
model = genai.GenerativeModel("gemini-2.0-flash-001")


@app.route("/report", methods=["GET"])
def report_ai():
    device_id = request.args.get("id")
    report_text = request.args.get("report")

    if not device_id or not report_text:
        return jsonify({"error": "Missing id or report"}), 400

    try:
        conn = mysql.connector.connect(**db_config)
        cur = conn.cursor(dictionary=True)

        # === IDs starting with U: User feedback, directly insert into report table with timestamp ===
        if device_id.startswith("U"):
            cur.execute(
                "INSERT INTO report (id, report, time) VALUES (%s, %s, %s)",
                (device_id, report_text, datetime.datetime.now())
            )
            conn.commit()
            cur.close()
            conn.close()
            return jsonify({"suggest": "User report has been recorded"}), 200

        # === IDs starting with D: Device issue, first query device name ===
        elif device_id.startswith("D"):
            cur.execute("SELECT name FROM device WHERE id = %s", (device_id,))
            row = cur.fetchone()
            if not row:
                cur.close()
                conn.close()
                return jsonify({"error": f"Device {device_id} not found"}), 404

            device_name = row["name"]

            # Construct prompt
            prompt = f"""
            Device name: {device_name}
            User reported problem: {report_text}

            Act as a professional device technical support staff, give possible causes and solutions.
            Requirements:
            1. Keep content concise, no unnecessary symbols, within 200 characters.
            2. Provide temporary workaround and final suggestion.
            3. Answer in Japanese.
            """

            # Call Gemini API
            response = model.generate_content(prompt)
            suggest = response.text.strip() if hasattr(response, "text") else str(response)

            # Insert device report + AI suggestion into database
            cur.execute(
                "INSERT INTO report (id, report, time) VALUES (%s, %s, %s)",
                (device_id, f"{report_text} | AI: {suggest}", datetime.datetime.now())
            )
            conn.commit()

            cur.close()
            conn.close()
            return jsonify({"suggest": suggest}), 200

        else:
            return jsonify({"error": "Invalid id format"}), 400

    except mysql.connector.Error as err:
        return jsonify({"error": str(err)}), 500

# === /get_report Retrieve all reports in descending order by time ===
@app.route("/get_report", methods=["GET"])
def get_report():
    try:
        conn = mysql.connector.connect(**db_config)
        with conn.cursor(dictionary=True) as cur:
            sql = """
                SELECT r.id,
                       CASE
                         WHEN r.id LIKE 'D%%' THEN d.name
                         WHEN r.id LIKE 'U%%' THEN u.name
                         ELSE NULL
                       END AS name,
                       r.report,
                       r.time
                FROM report r
                LEFT JOIN device d ON r.id = d.id
                LEFT JOIN user   u ON r.id = u.id
                ORDER BY r.time DESC
            """
            cur.execute(sql)
            rows = cur.fetchall()

        conn.close()
        return jsonify(rows), 200

    except mysql.connector.Error as err:
        return jsonify({"error": str(err)}), 500


if __name__ == "__main__":
    # Listen on all addresses, port 8001
    app.run(host="0.0.0.0", port=8001, debug=True)
