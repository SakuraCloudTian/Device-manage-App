from flask import Flask, jsonify,request
import mysql.connector
from flask_cors import CORS
import win32print, win32ui
from PIL import Image, ImageDraw, ImageFont, ImageWin
import qrcode
from qrcode.constants import ERROR_CORRECT_L

app = Flask(__name__)
CORS(app, resources={r"/": {"origins": "*"}})
# === Database config ===
db_config = {
    'user': 'yinkuns7',
    'password': 'abc123',
    'host': '192.168.10.8',
    'database': 'device'
}

@app.route("/get_device", methods=["GET"])
def get_device():
    try:
        # Connect to DB
        conn = mysql.connector.connect(**db_config)
        cur = conn.cursor()

        # Query all rows
        cur.execute("SELECT * FROM device;")
        rows = cur.fetchall()

        # Column names
        col_names = [desc[0] for desc in cur.description]

        # Convert to list of dicts
        result = [dict(zip(col_names, row)) for row in rows]

        cur.close()
        conn.close()

        return jsonify(result), 200

    except mysql.connector.Error as err:
        return jsonify({"error": str(err)}), 500

@app.route("/lend", methods=["GET"])
def lend_device():
    device_id = request.args.get("id")
    user_id = request.args.get("curr_user")   # The uploaded value is actually user_id
    status_str = request.args.get("status")
    return_date = request.args.get("return_date")
    out_date = request.args.get("out_date")

    if not device_id:
        return jsonify({"error": "Missing device id"}), 400
    if not user_id:
        return jsonify({"error": "Missing user id"}), 400

    # status conversion: true -> 0 (lent out), false -> 1 (available)
    status = 0 if status_str and status_str.lower() == "true" else 1

    try:
        conn = mysql.connector.connect(**db_config)
        cur = conn.cursor(dictionary=True)

        # 1. Check device status
        cur.execute("SELECT status FROM device WHERE id = %s", (device_id,))
        row = cur.fetchone()

        if not row:
            return jsonify({"error": f"Device {device_id} not found"}), 404

        if row["status"] == 1:
            return jsonify({"error": "Device already lent out"}), 400

        # 2. Find user name from user table
        cur.execute("SELECT name FROM user WHERE id = %s", (user_id,))
        user_row = cur.fetchone()
        if not user_row:
            return jsonify({"error": f"User {user_id} not found"}), 404

        user_name = user_row["name"]

        # 3. Update device table
        update_sql = """
            UPDATE device
            SET 
                curr_user = %s,
                status = %s,
                return_date = %s,
                out_date = %s
            WHERE id = %s
        """
        cur.execute(update_sql, (user_name, status, return_date, out_date, device_id))
        conn.commit()

        return jsonify({"message": "Update success"}), 200

    except mysql.connector.Error as err:
        return jsonify({"error": str(err)}), 500

    finally:
        if cur: cur.close()
        if conn: conn.close()


@app.route("/return", methods=["GET"])
def return_device():
    device_id = request.args.get("id")
    user_id = request.args.get("curr_user")   # The uploaded value is actually user_id
    return_date = request.args.get("return_date")
    location = request.args.get("location")

    if not device_id:
        return jsonify({"error": "Missing device id"}), 400
    if not user_id:
        return jsonify({"error": "Missing user id"}), 400

    try:
        conn = mysql.connector.connect(**db_config)
        cur = conn.cursor(dictionary=True)

        # 1. Find user name
        cur.execute("SELECT name FROM user WHERE id = %s", (user_id,))
        user_row = cur.fetchone()
        if not user_row:
            return jsonify({"error": f"User {user_id} not found"}), 404

        user_name = user_row["name"]

        # 2. Check if device.curr_user equals the provided user name
        cur.execute("SELECT curr_user, his_user, status FROM device WHERE id = %s", (device_id,))
        device_row = cur.fetchone()
        if not device_row:
            return jsonify({"error": f"Device {device_id} not found"}), 404

        if device_row["curr_user"] != user_name:
            return jsonify({"error": "User does not match the current device user"}), 400

        # 3. Append to his_user (history users)
        his_user_old = device_row["his_user"] if device_row["his_user"] else ""
        if his_user_old:
            his_user_new = his_user_old + "," + user_name
        else:
            his_user_new = user_name

        # 4. Update device table
        update_sql = """
            UPDATE device
            SET 
                curr_user = NULL,
                status = 0,
                his_user = %s,
                return_date = %s,
                location = %s
            WHERE id = %s
        """
        cur.execute(update_sql, (his_user_new, return_date, location, device_id))
        conn.commit()

        return jsonify({"message": "Device returned successfully"}), 200

    except mysql.connector.Error as err:
        return jsonify({"error": str(err)}), 500

    finally:
        if cur: cur.close()
        if conn: conn.close()



@app.route("/regis_user", methods=["GET"])
def regis_user():
    user_id = request.args.get("id")
    name = request.args.get("name")
    grade = request.args.get("grade")
    contact = request.args.get("contact")
    admin_str = request.args.get("admin")

    if not user_id or not name:
        return jsonify({"error": "Missing required fields: id or name"}), 400

    # admin conversion: true -> 1, false -> 0
    admin = 1 if admin_str and admin_str.lower() == "true" else 0

    try:
        conn = mysql.connector.connect(**db_config)
        cur = conn.cursor()

        insert_sql = """
            INSERT INTO user (id, name, grade, contact, admin)
            VALUES (%s, %s, %s, %s, %s)
        """
        cur.execute(insert_sql, (user_id, name, grade, contact, admin))
        conn.commit()

        return jsonify({"message": "User registered successfully"}), 200

    except mysql.connector.Error as err:
        return jsonify({"error": str(err)}), 500

    finally:
        if cur: cur.close()
        if conn: conn.close()

@app.route("/regis_device", methods=["GET"])
def regis_device():
    device_id = request.args.get("id")
    name = request.args.get("name")
    status_str = request.args.get("status")
    location = request.args.get("location")

    if not device_id or not name:
        return jsonify({"error": "Missing required fields: id or name"}), 400

    # status conversion: true -> 0 (lent out), false -> 1 (available)
    status = 0 if status_str and status_str.lower() == "true" else 1

    try:
        conn = mysql.connector.connect(**db_config)
        cur = conn.cursor()

        insert_sql = """
            INSERT INTO device (id, name, status, location)
            VALUES (%s, %s, %s, %s)
        """
        cur.execute(insert_sql, (device_id, name, status, location))
        conn.commit()

        return jsonify({"message": "Device registered successfully"}), 200

    except mysql.connector.Error as err:
        return jsonify({"error": str(err)}), 500

    finally:
        if cur: cur.close()
        if conn: conn.close()

# @app.route("/report", methods=["GET"])
# def report_device():
#     device_id = request.args.get("id")
#     report_text = request.args.get("report")
#
#     if not device_id or not report_text:
#         return jsonify({"error": "Missing required fields: id or report"}), 400
#
#     try:
#         conn = mysql.connector.connect(**db_config)
#         cur = conn.cursor()
#
#         # 1. Check if id already exists
#         cur.execute("SELECT id FROM report WHERE id = %s", (device_id,))
#         row = cur.fetchone()
#
#         if row:
#             # Exists → update
#             update_sql = "UPDATE report SET report = %s WHERE id = %s"
#             cur.execute(update_sql, (report_text, device_id))
#         else:
#             # Does not exist → insert
#             insert_sql = "INSERT INTO report (id, report) VALUES (%s, %s)"
#             cur.execute(insert_sql, (device_id, report_text))
#
#         conn.commit()
#
#         return jsonify({"message": "Report updated successfully"}), 200
#
#     except mysql.connector.Error as err:
#         return jsonify({"error": str(err)}), 500
#
#     finally:
#         if cur: cur.close()
#         if conn: conn.close()

@app.route("/get_name", methods=["GET"])
def get_name():
    user_id = request.args.get("user_id")
    devicec_id = request.args.get("device_id")

    if not user_id and not devicec_id:
        return jsonify({"error": "Missing required parameters"}), 400

    try:
        conn = mysql.connector.connect(**db_config)
        cur = conn.cursor(dictionary=True)

        result = {}

        # Query user name
        if user_id:
            cur.execute("SELECT name FROM user WHERE id = %s", (user_id,))
            row = cur.fetchone()
            if row:
                result["user_name"] = row["name"]
            else:
                result["user_name"] = None

        # Query device name
        if devicec_id:
            cur.execute("SELECT name FROM device WHERE id = %s", (devicec_id,))
            row = cur.fetchone()
            if row:
                result["device_name"] = row["name"]
            else:
                result["device_name"] = None

        cur.close()
        conn.close()

        if not result:
            return jsonify({"error": "No matching data found"}), 404

        return jsonify(result), 200

    except mysql.connector.Error as err:
        return jsonify({"error": str(err)}), 500
# === Extra parameters (can be put into global config) ===
TEXT_MM   = 6.0          # Height of text area below QR code (mm)
LEFT_MM   = 0.0          # Left margin from printable area (mm)
TOP_MM    = 0.0          # Top margin from printable area (mm)
FONT_PT   = 24           # Font size (points)
FONT_PATH = None         # Custom font path (e.g., C:/Windows/Fonts/arial.ttf), leave blank for auto-detect

def compose_qr_with_text(qr_img_1bit: Image.Image, qr_px: int, text_px_h: int,
                         text: str, font_pt: int) -> Image.Image:
    """
    Generate "QR code + text below" composite image (RGB), width=qr_px, height=qr_px+text_px_h
    - QR code is placed at the top-left
    - Text is horizontally centered by default (set text_x=0 for left alignment)
    """
    # Use your existing build_exact_size, paste at top-left
    qr_exact = build_exact_size(qr_img_1bit, qr_px).convert("RGB")

    total_w = qr_px
    total_h = qr_px + text_px_h
    canvas = Image.new("RGB", (total_w, total_h), "white")
    canvas.paste(qr_exact, (0, 0))

    draw = ImageDraw.Draw(canvas)

    # Prepare font
    font = None
    if FONT_PATH:
        try:
            font = ImageFont.truetype(FONT_PATH, font_pt)
        except Exception:
            font = None
    if font is None:
        # Fallback common fonts
        for p in [
            r"C:\Windows\Fonts\msyh.ttc",
            r"C:\Windows\Fonts\arial.ttf",
            r"C:\Windows\Fonts\calibri.ttf",
            r"C:\Windows\Fonts\msmincho.ttc",
        ]:
            try:
                font = ImageFont.truetype(p, font_pt)
                break
            except Exception:
                font = None
    if font is None:
        font = ImageFont.load_default()

    # Text size and position (centered)
    tw, th = draw.textsize(text, font=font)
    text_x = max(0, (total_w - tw) // 2)
    text_y = qr_px + max(0, (text_px_h - th) // 2)

    draw.text((text_x, text_y), text, fill="black", font=font)
    return canvas  # RGB

TARGET_MM = 21.0     # Physical size (mm)
QR_VERSION = 1
QR_ERR = ERROR_CORRECT_L
QR_BORDER = 1
QR_BOX = 3
PRINTER_NAME = "MF731C/733C"   # Change to your printer name

def mm_to_px(mm, dpi):
    return int(round(mm / 25.4 * dpi))

def make_qr_image(payload: str) -> Image.Image:
    qr = qrcode.QRCode(
        version=QR_VERSION,
        error_correction=QR_ERR,
        box_size=QR_BOX,
        border=QR_BORDER
    )
    qr.add_data(payload)
    qr.make(fit=False)  # Fixed version=1
    return qr.make_image(fill_color="black", back_color="white").convert("1")

def build_exact_size(img: Image.Image, target_px: int) -> Image.Image:
    modules = 21
    total_modules = modules + 2 * QR_BORDER
    ppm = max(1, target_px // total_modules)
    scaled = img.resize((total_modules * ppm, total_modules * ppm), resample=Image.NEAREST)

    if scaled.size[0] == target_px:
        return scaled

    canvas = Image.new("1", (target_px, target_px), 1)
    ox = (target_px - scaled.size[0]) // 2
    oy = (target_px - scaled.size[1]) // 2
    canvas.paste(scaled, (ox, oy))
    return canvas


@app.route("/print", methods=["GET"])
def print_qr():
    device_id = request.args.get("id")
    if not device_id:
        return jsonify({"error": "Missing device id"}), 400

    hDC = None
    try:
        # Open printer
        hDC = win32ui.CreateDC()
        hDC.CreatePrinterDC(PRINTER_NAME)

        # Query printer capabilities
        LOGPIXELSX, LOGPIXELSY = 88, 90
        HORZRES, VERTRES = 8, 10
        PHYSICALOFFSETX, PHYSICALOFFSETY = 112, 113

        dpi_x = hDC.GetDeviceCaps(LOGPIXELSX)
        dpi_y = hDC.GetDeviceCaps(LOGPIXELSY)
        printable_w = hDC.GetDeviceCaps(HORZRES)
        printable_h = hDC.GetDeviceCaps(VERTRES)
        off_x = hDC.GetDeviceCaps(PHYSICALOFFSETX)
        off_y = hDC.GetDeviceCaps(PHYSICALOFFSETY)

        # Use DPI in X direction (usually the same for x/y)
        dpi = dpi_x

        # Calculate pixel dimensions
        qr_px   = min(mm_to_px(TARGET_MM, dpi_x), mm_to_px(TARGET_MM, dpi_y))
        text_px = min(mm_to_px(TEXT_MM,   dpi_x), mm_to_px(TEXT_MM,   dpi_y))
        left_px = mm_to_px(LEFT_MM, dpi_x)
        top_px  = mm_to_px(TOP_MM,  dpi_y)

        # Check printable area (QR + text total size)
        total_w = qr_px
        total_h = qr_px + text_px
        if total_w > printable_w or total_h > printable_h:
            return jsonify({"error": "QR + text exceeds printable area"}), 500

        # Generate QR code & composite image with text
        qr_img = make_qr_image(device_id)               # 1-bit
        composed = compose_qr_with_text(qr_img, qr_px, text_px, device_id, FONT_PT)  # RGB

        # Place at top-left of page (relative to printable area + physical offset)
        left = off_x + left_px
        top  = off_y + top_px

        # Print
        hDC.StartDoc(f"QR_{device_id}_21mm_with_ID")
        try:
            hDC.StartPage()
            dib = ImageWin.Dib(composed)
            w, h = composed.size
            # Draw directly in pixels, no scaling
            dib.draw(hDC.GetHandleOutput(), (left, top, left + w, top + h))
            hDC.EndPage()
        finally:
            hDC.EndDoc()

        return jsonify({"message": f"Print job sent to {PRINTER_NAME} for device {device_id}"}), 200

    except Exception as e:
        return jsonify({"error": str(e)}), 500
    finally:
        if hDC:
            try:
                hDC.DeleteDC()
            except Exception:
                pass

if __name__ == "__main__":
    # host='0.0.0.0' allows other devices in LAN to access
    app.run(host="0.0.0.0", port=8000)
