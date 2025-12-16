from flask import Flask, request, jsonify
import os
import cv2
import numpy as np
from ultralytics import YOLO
from collections import Counter
import mediapipe as mp
import requests
import pandas as pd
from sklearn.metrics.pairwise import cosine_similarity
import json

# -------------------------------------------------
# Flask 初始化
app = Flask(__name__)

# -------------------------------------------------
# 專案根目錄（關鍵）
BASE_DIR = os.path.dirname(os.path.abspath(__file__))

# -------------------------------------------------
# 上傳資料夾（相對路徑）
UPLOAD_FOLDER = os.path.join(BASE_DIR, 'uploads')
REALTIME_UPLOADS = os.path.join(BASE_DIR, 'realtimeuploads')

os.makedirs(UPLOAD_FOLDER, exist_ok=True)
os.makedirs(REALTIME_UPLOADS, exist_ok=True)

app.config['UPLOAD_FOLDER'] = UPLOAD_FOLDER

# -------------------------------------------------
# YOLO 模型（相對路徑）
MODEL_PATH = os.path.join(BASE_DIR, 'best.pt')
model = YOLO(MODEL_PATH)

# -------------------------------------------------
# 允許檔案格式
ALLOWED_EXTENSIONS = {'mp4', 'avi', 'mov', 'mkv', 'jpg', 'jpeg', 'png', 'bmp'}

def allowed_file(filename):
    return '.' in filename and filename.rsplit('.', 1)[1].lower() in ALLOWED_EXTENSIONS

# -------------------------------------------------
# MediaPipe Pose 與標準資料（相對路徑）
mp_pose = mp.solutions.pose

STANDARD_CSV = os.path.join(BASE_DIR, '0_17frame100.csv')
standard_df = pd.read_csv(STANDARD_CSV)

def extract_keypoints(landmarks):
    return np.array([(lm.x, lm.y, lm.z) for lm in landmarks]).flatten()

def sequence_to_sequence_score(user_seq, standard_seq):
    min_len = min(len(user_seq), len(standard_seq))
    cos_scores, dist_penalties = [], []

    for i in range(min_len):
        cos = cosine_similarity([user_seq[i]], [standard_seq[i]])[0][0]
        dist = np.linalg.norm(user_seq[i] - standard_seq[i])
        cos_scores.append(cos)
        dist_penalties.append(dist)

    final_score = np.mean(cos_scores) * 100 - np.mean(dist_penalties) * 35
    return round(max(final_score, 0), 2)

def analyze_video_cosine(video_path, action_label):
    cap = cv2.VideoCapture(video_path)
    vectors, frame_count = [], 0

    with mp_pose.Pose(static_image_mode=False, min_detection_confidence=0.5, model_complexity=1) as pose:
        while cap.isOpened() and len(vectors) < 100:
            ret, frame = cap.read()
            if not ret:
                break
            if frame_count % 5 == 0:
                results = pose.process(cv2.cvtColor(frame, cv2.COLOR_BGR2RGB))
                if results.pose_landmarks:
                    vectors.append(extract_keypoints(results.pose_landmarks.landmark))
            frame_count += 1

    cap.release()
    if len(vectors) < 30:
        return None

    std_seq = standard_df[standard_df['label'] == int(action_label)].sort_values('frame')
    return sequence_to_sequence_score(np.array(vectors[:100]), std_seq.drop(columns=['label', 'frame']).values)

# -------------------------------------------------
# 動作對照表（相對路徑）
EXERCISE_CSV = os.path.join(BASE_DIR, 'exercise.csv')
df = pd.read_csv(EXERCISE_CSV)
df_dict = df.set_index('編號')['中文'].to_dict()

def get_key(val):
    return df_dict.get(val, "no such key")

# -------------------------------------------------
# 推薦影片資料庫（相對路徑）
VIDEO_CSV = os.path.join(BASE_DIR, 'final_ex.csv')
video_df = pd.read_csv(VIDEO_CSV)

def build_prompt(user_input, video_df):
    video_text = "\n".join(
        f"{i+1}. {row['中文']} - {row['網址']}"
        for i, row in video_df.iterrows()
    )

    return f"""
你是一位專業健身教練，請從下列影片清單中，挑選最適合使用者需求的 3 部影片，並回傳 JSON 格式：

影片清單：
{video_text}

使用者需求：「{user_input}」

只回傳純 JSON。
"""

# -------------------------------------------------
# 推薦 API
@app.route("/recommend", methods=["POST"])
def recommend():
    user_input = request.json.get("body_part", "").strip()
    prompt = build_prompt(user_input, video_df)

    res = requests.post("http://localhost:11434/api/generate", json={
        "model": "llama3.2",
        "prompt": prompt,
        "stream": False
    })

    return jsonify(json.loads(res.json()["response"]))

# -------------------------------------------------
# 上傳影片
@app.route('/uploads', methods=['POST'])
def upload_file():
    file = request.files.get('file')
    if not file or not allowed_file(file.filename):
        return jsonify({"error": "Invalid file"}), 400

    save_path = os.path.join(UPLOAD_FOLDER, file.filename)
    file.save(save_path)

    return jsonify({"file_path": save_path, "filename": file.filename})

# -------------------------------------------------
# 預測結果
@app.route('/prediction-result', methods=['GET'])
def prediction_result():
    video_id = request.args.get('videoId')
    video_path = os.path.join(UPLOAD_FOLDER, video_id)

    results = model.predict(source=video_path, conf=0.5, save=False)

    preds, confs = [], []
    for r in results:
        for cls, conf in zip(r.boxes.cls, r.boxes.conf):
            preds.append(int(cls.item()))
            confs.append(conf.item())

    most_common = Counter(preds).most_common(1)[0][0]
    avg_conf = round(np.mean([c for p, c in zip(preds, confs) if p == most_common]), 3)

    score = analyze_video_cosine(video_path, most_common)
    chinese = get_key(most_common)

    return jsonify({
        "predicted_class": most_common,
        "chinese_id": chinese,
        "average_confidence": avg_conf,
        "score": score
    })

# -------------------------------------------------
# 啟動伺服器
if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)
