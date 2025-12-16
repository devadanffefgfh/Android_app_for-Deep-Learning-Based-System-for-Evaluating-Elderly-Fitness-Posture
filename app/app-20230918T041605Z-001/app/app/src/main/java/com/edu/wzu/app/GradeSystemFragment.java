package com.edu.wzu.app;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ModuleInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.core.*;
import androidx.camera.view.PreviewView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.edu.wzu.app.databinding.FragmentGradeSystemBinding;
import com.google.gson.Gson;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;


public class GradeSystemFragment extends Fragment {
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;
    private static final int STORAGE_PERMISSION_REQUEST_CODE = 101;
    private static final int CAMERA_REQUEST_CODE = 102;
    private static final int VIDEO_PERMISSION_REQUEST_CODE = 103;
    private static final int VIDEO_REQUEST_CODE = 104;
    private Uri videoUri;
    private static final String TAG = "GradeSystemFragment";
    private Uri imageUri;
    private UrlViewModel urlViewModel;
    private FragmentGradeSystemBinding binding;
    private static final int PICK_VIDEO_REQUEST = 1;
    private Uri selectedVideoUri;
    private ApiService apiService;
    private Module pytorchModule;
    private SurfaceView cameraPreview;
    private TextView resultText,predText,ai_text;
    private static final int CAMERA_PERMISSION_REQUEST = 200;
    public GradeSystemFragment() {
        // Required empty public constructor
    }


    public static GradeSystemFragment newInstance(String param1, String param2) {
        GradeSystemFragment fragment = new GradeSystemFragment();
        Bundle args = new Bundle();
        args.putString("param1", param1);
        args.putString("param2", param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            String mParam1 = getArguments().getString("param1");
            String mParam2 = getArguments().getString("param2");
        }
//        try {
//            // Load PyTorch model
//            pytorchModule = Module.load(assetFilePath("best.torchscript"));
//        } catch (Exception e) {
//            Log.e("PyTorch", "Error loading model", e);
//        }
        //checkPermissionsAndRequest();
    }


    // 播放錄製的影片


    private void playRecordedVideo(Uri videoUri) {
        if (videoUri == null) {
            Log.e(TAG, "Video URI is null!");
            return;
        }

        VideoView videoView = binding.videoView3;
        videoView.setVisibility(View.VISIBLE);
        videoView.setVideoURI(videoUri); // 直接使用 URI，不需要轉換為路徑

        MediaController mediaController = new MediaController(getContext());
        mediaController.setAnchorView(videoView);
        videoView.setMediaController(mediaController);

        videoView.requestFocus();
        videoView.start();

        videoView.setOnCompletionListener(mp -> Log.d(TAG, "Video playback completed"));
        videoView.setOnErrorListener((mp, what, extra) -> {
            Log.e(TAG, "Error occurred during video playback");
            return true;
        });
    }




    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        urlViewModel = new ViewModelProvider(this).get(UrlViewModel.class);
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_grade_system, container, false);
        binding.setData(urlViewModel);
        binding.setLifecycleOwner(this);

        View view = binding.getRoot();
        checkPermissionsAndOpenCamera();
        checkPermissionsAndOpenVideoRecorder();
        checkCameraPermission();


        ImageButton uploadButton = view.findViewById(R.id.uploadButton);

        apiService = RetrofitClient.getClient().create(ApiService.class);
        if (apiService == null) {
            throw new NullPointerException("API Service 初始化失败");
        }

        ImageButton imageButton = view.findViewById(R.id.CameraButton);
        Button deleteAllBtn = view.findViewById(R.id.DeleteAllButton);
        //ImageView imageView = view.findViewById(R.id.imageView);
        ImageButton videoButton = view.findViewById(R.id.videoimageButton);
        //ImageButton predButton = view.findViewById(R.id.pred_Button);
        cameraPreview = view.findViewById(R.id.cameraPreview);

        resultText = view.findViewById(R.id.resultText);
        predText = view.findViewById((R.id.scoretextView));
        ai_text = view.findViewById(R.id.ai_suggestiontextView);

        EditText inputEditText = view.findViewById(R.id.bodyPartInput);
        Button generateButton = view.findViewById(R.id.generateButton);
        TextView resultTextView = view.findViewById(R.id.resultText); // 用來顯示回傳內容
        //搜尋推薦
        generateButton.setOnClickListener(v -> {
            String input = inputEditText.getText().toString().trim();
            if (input.isEmpty()) {
                inputEditText.setError("請輸入鍛鍊部位");
                return;
            }

            RecommendRequest request = new RecommendRequest(input);


            Call<RecommendResponse> call = apiService.getVideoRecommendation(request);

            call.enqueue(new Callback<RecommendResponse>() {
                @Override
                public void onResponse(Call<RecommendResponse> call, Response<RecommendResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        RecommendResponse result = response.body();
                        List<RecommendResponse.Recommendation> list = result.getRecommendations();

                        if (list == null || list.isEmpty()) {
                            resultTextView.setText("⚠️ 沒有找到相關影片");
                            return;
                        }

                        StringBuilder sb = new StringBuilder();
                        for (RecommendResponse.Recommendation r : list) {
                            sb.append("✅ ").append(r.getTitle()).append("\n👉 ").append(r.getUrl()).append("\n\n");
                        }

                        resultTextView.setText(sb.toString());
                        resultTextView.setMovementMethod(LinkMovementMethod.getInstance());
                    } else {
                        resultTextView.setText("❌ 錯誤，回傳格式不正確！");
                    }
                }

                @Override
                public void onFailure(Call<RecommendResponse> call, Throwable t) {
                    resultTextView.setText("🚨 發生錯誤：" + t.getMessage());
                }
            });
        });

        // 按鈕點擊事件
        //預測
//        predButton.setOnClickListener(v -> {
//            Log.d("ButtonClick", "predButton clicked!");
//            cameraPreview.setVisibility(View.VISIBLE); // 確保SurfaceView可見
//
//            //startCameraPreview();
//
//        });
        //上傳
        uploadButton.setOnClickListener(v -> openVideoPicker());
        // 點擊拍照按鈕時檢查權限並打開相機
        imageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCamera();
            }
        });
        // 點擊錄影按鈕時檢查權限並打開錄影界面
        videoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openVideoRecorder();
            }
        });


        // 點擊刪除所有圖片按鈕時彈出確認對話框
        deleteAllBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteAllImagesFromDatabase();
            }
        });
        // 觀察 imageUris 的變化，更新 UI 上的圖片顯示
//        urlViewModel.getImageUris().observe(getViewLifecycleOwner(), new Observer<List<String>>() {
//            @Override
//            public void onChanged(List<String> uris) {
//                if (!uris.isEmpty()) {
//                    String lastUri = uris.get(uris.size() - 1);
//                    imageView.setImageURI(Uri.parse(lastUri));
//                } else {
//                    imageView.setImageURI(null);
//                }
//            }
//        });
//        urlViewModel.getImageUris().observe(getViewLifecycleOwner(), new Observer<List<String>>() {
//            @Override
//            public void onChanged(List<String> uris) {
//                if (uris != null && !uris.isEmpty()) {
//                    String lastUri = uris.get(uris.size() - 1);
//                    imageView.setImageURI(Uri.parse(lastUri));
//                } else {
//                    imageView.setImageResource(R.drawable.baseline_image_24); // 替換為你的佔位圖片
//                }
//            }
//        });


        return view;
    }





    //----------------------------------------------------
    // 啟動影片選擇器
    private void openVideoPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); // 添加讀取 URI 權限
        startActivityForResult(intent, PICK_VIDEO_REQUEST);

    }
    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.CAMERA}, 100);
        }
    }

    private void checkPermissionsAndOpenCamera() {
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
        } else {
            //openCamera();
        }
    }

    private void checkPermissionsAndOpenVideoRecorder() {
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.RECORD_AUDIO}, VIDEO_PERMISSION_REQUEST_CODE);
        } else {
            //openVideoRecorder();
        }
    }


    // 開啟錄影應用
    private void openVideoRecorder() {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.TITLE, "My Video");
            values.put(MediaStore.Video.Media.DESCRIPTION, "Recorded with my camera");
            values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            // 將錄影視頻 URI 插入到系統媒體庫
            videoUri = getContext().getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
            Log.d(TAG, "Video URI: " + videoUri);
            // 啟動錄影應用，錄製視頻並將結果保存到指定 URI
            Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
            if (intent.resolveActivity(getContext().getPackageManager()) != null) {
                intent.putExtra(MediaStore.EXTRA_OUTPUT, videoUri);
                startActivityForResult(intent, VIDEO_REQUEST_CODE);
            } else {
                Log.e(TAG, "No video app available");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error opening video recorder", e);
        }
    }

    //相機
    private void checkPermissionsAndRequest() {
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(getActivity(),
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST_CODE);
        } else if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(getActivity(),
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    STORAGE_PERMISSION_REQUEST_CODE);
        } // 检查录音权限
        else if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(getActivity(),
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    VIDEO_PERMISSION_REQUEST_CODE);
        }
    }


    private void openCamera() {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.TITLE, "My Picture");
            values.put(MediaStore.Images.Media.DESCRIPTION, "Taken with my camera");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            // 將圖片 URI 插入到系統媒體庫
            imageUri = getContext().getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            Log.d(TAG, "Image URI: " + imageUri);
            // 啟動相機應用，拍照並將結果保存到指定 UR
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (intent.resolveActivity(getContext().getPackageManager()) != null) {
                intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
                startActivityForResult(intent, CAMERA_REQUEST_CODE);
            } else {
                Log.e(TAG, "No camera app available");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error opening camera", e);
        }
    }
    //上傳影片到伺服器
//    private void uploadVideo(Uri videoUri) {
//        Log.d(TAG, "Uploading video from URI: " + videoUri);
//        try {
//            File videoFile = getFileFromUri(videoUri);
//            Log.d(TAG, "Video file path: " + videoFile.getAbsolutePath());
//
//            RequestBody requestFile = RequestBody.create(MediaType.parse("video/*"), videoFile);
//            MultipartBody.Part body = MultipartBody.Part.createFormData("file", videoFile.getName(), requestFile);
//
//            Call<UploadResponse> call = apiService.uploadVideo(body);
//            call.enqueue(new Callback<UploadResponse>() {
//                @Override
//                public void onResponse(Call<UploadResponse> call, Response<UploadResponse> response) {
//                    if (response.isSuccessful()) {
//                        Log.d(TAG, "Upload success: " + response.body());
//                        Toast.makeText(getContext(), "Upload Success!", Toast.LENGTH_SHORT).show();
//                    } else {
//                        Log.e(TAG, "Upload failed: " + response.code());
//                        Toast.makeText(getContext(), "Upload Failed! Code: " + response.code(), Toast.LENGTH_SHORT).show();
//                    }
//                }
//
//                @Override
//                public void onFailure(Call<UploadResponse> call, Throwable t) {
//                    Log.e(TAG, "Upload error: " + t.getMessage());
//                    Toast.makeText(getContext(), "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
//                }
//            });
//
//        } catch (Exception e) {
//            Log.e(TAG, "File conversion error: " + e.getMessage());
//            Toast.makeText(getContext(), "File Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
//        }
//    }
    //-------------------------
    private ProgressDialog progressDialog;

    private void uploadVideo(Uri videoUri) {
        Log.d(TAG, "Uploading video from URI: " + videoUri);
        try {
            // 显示进度对话框
            showProgressDialog("Uploading video...");

            File videoFile = getFileFromUri(videoUri);
            RequestBody requestFile = RequestBody.create(MediaType.parse("video/*"), videoFile);
            MultipartBody.Part body = MultipartBody.Part.createFormData("file", videoFile.getName(), requestFile);

            Call<UploadResponse> uploadCall = apiService.uploadVideo(body);
            uploadCall.enqueue(new Callback<UploadResponse>() {
                @Override
                public void onResponse(Call<UploadResponse> call, Response<UploadResponse> response) {
                    dismissProgressDialog();
                    if (response.isSuccessful() && response.body() != null) {
                        Log.d(TAG, "Upload success: " + response.body().getMessage());
                        Toast.makeText(getContext(), "Upload Success!", Toast.LENGTH_SHORT).show();

                        // 从返回数据中提取文件名作为 videoId
                        String videoId = response.body().getFilename(); // 确保 API 返回包含 filename 字段
                        Log.e(TAG,"111111"+videoId);
                        if (videoId != null && !videoId.isEmpty()) {
                            // ✅ 顯示影片到 VideoView
                            playRecordedVideo(videoUri);
                            // 开始获取预测结果
                            fetchPredictionResult(videoId);
                        } else {
                            Log.e(TAG, "Filename missing in response!");
                            Toast.makeText(getContext(), "Upload response invalid: missing filename", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.e(TAG, "Upload failed: " + response.code());
                        Toast.makeText(getContext(), "Upload Failed! Code: " + response.code(), Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<UploadResponse> call, Throwable t) {
                    dismissProgressDialog();
                    Log.e(TAG, "Upload error: " + t.getMessage());
                    Toast.makeText(getContext(), "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            dismissProgressDialog();
            Log.e(TAG, "File conversion error: " + e.getMessage());
            Toast.makeText(getContext(), "File Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void fetchPredictionResult(String videoId) {
        showProgressDialog("預測中...");
        Call<PredictionResponse> predictionCall = apiService.getPredictionResult(videoId);

        predictionCall.enqueue(new Callback<PredictionResponse>() {
            @Override
            public void onResponse(Call<PredictionResponse> call, Response<PredictionResponse> response) {
                dismissProgressDialog();
                Log.d(TAG, "Response body: " + new Gson().toJson(response.body())); // 打印返回 JSON
                Log.d(TAG, "Raw Response: " + response.toString());
                Log.d(TAG, "Response Body String: " + response.errorBody());

                if (response.isSuccessful() && response.body() != null) {
                    PredictionResponse predictionResponse = response.body();
                    String result = "Prediction: \n" + predictionResponse.getPrediction()+predictionResponse.getChinese_id()+
                            "\nConfidence: \n"+predictionResponse.getConfidence()+
                            "\nScore: \n"+predictionResponse.getScore(); // 对应后端字段
                    String result2 = "建議: "+predictionResponse.getAi_suggestion();
                    predText.setText(result);
                    ai_text.setText(result2);
                    resultText.setVisibility(View.VISIBLE);
                    Log.d(TAG, "Prediction result: " + result);
                } else {
                    Log.e(TAG, "Failed to fetch prediction result: " + response.code());
                    Toast.makeText(getContext(), "Failed to fetch prediction result!", Toast.LENGTH_SHORT).show();
                }
            }



            @Override
            public void onFailure(Call<PredictionResponse> call, Throwable t) {
                dismissProgressDialog();
                Log.e(TAG, "Error fetching prediction result: " + t.getMessage());
                Toast.makeText(getContext(), "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // 显示进度对话框
    private void showProgressDialog(String message) {
        if (progressDialog == null) {
            progressDialog = new ProgressDialog(getContext());
            progressDialog.setCancelable(false);
        }
        progressDialog.setMessage(message);
        progressDialog.show();
    }

    // 隐藏进度对话框
    private void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    //-------------------------

    // 將 Uri 轉換成 File
    private File getFileFromUri(Uri uri) throws IOException {
        ContentResolver contentResolver = getContext().getContentResolver();
        File cacheFile = new File(getContext().getCacheDir(), "upload_video.mp4");
        try (InputStream inputStream = contentResolver.openInputStream(uri);
             FileOutputStream outputStream = new FileOutputStream(cacheFile)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
        return cacheFile;
    }



    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult called with requestCode: " + requestCode + " and resultCode: " + resultCode);

        // 拍照處理
        if (requestCode == CAMERA_REQUEST_CODE) {
            if (resultCode == getActivity().RESULT_OK && imageUri != null) {
                urlViewModel.insertUri(imageUri.toString(), "image");
                urlViewModel.setMessage("照片已存儲，URI：" + imageUri.toString());
                Log.d(TAG,"22222"+imageUri.toString());
            } else {
                Log.e(TAG, "拍照取消或失敗");
                if (imageUri != null) {
                    getContext().getContentResolver().delete(imageUri, null, null);
                    Log.d(TAG, "Image URI 已刪除：" + imageUri);
                }
            }
        }
        if (data != null && data.getData() != null) {
            videoUri = data.getData(); // 获取返回的视频 URI
            getActivity().getContentResolver().takePersistableUriPermission(
                    videoUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            );
        }

        // 錄影處理
        if (requestCode == VIDEO_REQUEST_CODE) {
            if (resultCode == getActivity().RESULT_OK && videoUri != null) {
                Log.d(TAG, "Video URI: " + videoUri);

                // 將 URI 插入到 ViewModel 中
                urlViewModel.insertUri(videoUri.toString(), "video");
                urlViewModel.setMessage("影片已存儲，URI：" + videoUri);

                // 播放錄製影片
                playRecordedVideo(videoUri);
            } else {
                Log.e(TAG, "錄影取消或失敗");
            }
        }
        //處理選擇影片 上傳
        if (requestCode == PICK_VIDEO_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            Uri selectedVideoUri = data.getData();

            // 授予持久化權限
            int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
            requireContext().getContentResolver().takePersistableUriPermission(selectedVideoUri, flags);

            // 在生命周期稳定时处理上传
            new Handler(Looper.getMainLooper()).post(() -> {
                // 繼續處理 URI，例如上傳檔案
                uploadVideo(selectedVideoUri);
            });

            //uploadVideo(selectedVideoUri);
        }

    }

    // 處理權限請求的結果

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Log.e(TAG, "Camera permission denied");
            }
        } else if (requestCode == VIDEO_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openVideoRecorder();
            } else {
                Log.e(TAG, "Video permission denied");
            }
        }else if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d("Permission", "Camera permission granted");
        } else {
            Log.e("Permission", "Camera permission denied");
        }
    }

    // 彈出確認對話框並刪除所有圖片
    private void deleteAllImagesFromDatabase() {
        Context context = getContext();
        if (context == null) {
            Log.e("Fragment", "Context 為 null，無法顯示對話框");
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("確認刪除");
        builder.setMessage("確定要刪除所有影片嗎？");
        builder.setPositiveButton("確定", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                urlViewModel.deleteAllUris();
                urlViewModel.setMessage("All videos deleted");

                // **清空 VideoView 並強制更新**
                VideoView videoView = binding.videoView3;
                videoView.stopPlayback(); // **停止播放，釋放資源**
                videoView.setVideoURI(null); // **設置 URI 為 null**
                //videoView.setVisibility(View.GONE); // **隱藏 VideoView**
                videoView.setVideoPath(""); // **清空播放器的記憶**
                videoUri = null; // **清除變數中的 URI**
                videoView.setBackgroundColor(Color.BLACK); // **遮住舊的影片畫面**
                // **強制 UI 更新**
                videoView.invalidate();
            }
        });
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }


}
