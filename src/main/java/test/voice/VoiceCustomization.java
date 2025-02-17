package test.voice;

import com.google.gson.*;
import okhttp3.*;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class VoiceCustomization {

    private static final Properties props = new Properties();

    static {
        try (InputStream is = VoiceCustomization.class.getResourceAsStream("/config.properties")) {
            InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
            props.load(reader);
        } catch (IOException e) {
            throw new RuntimeException("加载配置文件失败", e);
        }
    }

    private static final String API_KEY = props.getProperty("voice.api.key");
    private static final String CONTEXT = props.getProperty("promoet");
    private static final String VOICE_URL = props.getProperty("voice.url");

    private static final int CHAT_COUNT_MAX = 20; //历史记录最大次数

    //设置结束词
    private static final String END_WORD1 = "拜拜";
    private static final String END_WORD2 = "再见";

    // 是否打印思考过程
    private static final boolean IS_REASONING_CONTENT_PRINT = Boolean.parseBoolean(props.getProperty("is_reasoning_content_print"));

    // 静音检测参数
    private static final double VOLUME_THRESHOLD = Double.parseDouble(props.getProperty("volume_threshold"));; // 音量阈值（根据环境调整）
    private static final long SILENCE_DURATION = Long.parseLong(props.getProperty("silence_duration"));;    // 静音持续时间（毫秒）
    private static  final long MAX_RECORD_TIME = Long.parseLong(props.getProperty("max_record_time"));;    // 最大录音时间（毫秒）

    private static final String STT_API_URL = "https://api.siliconflow.cn/v1/audio/transcriptions";
    private static final String TTS_API_URL = "https://api.siliconflow.cn/v1/audio/speech";
    private static final String AID_API_URL = "https://api.siliconflow.cn/v1/chat/completions";
    private static final Path AUDIO_PATH = Path.of("recording.wav");
    private static final Path OUTPUT_PATH = Path.of("output.wav");

    private static final OkHttpClient OK_HTTP_CLIENT_AI_CHAT = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)  // 连接超时：60秒
            .readTimeout(120, TimeUnit.SECONDS)    // 读取超时：120秒
            .writeTimeout(30, TimeUnit.SECONDS)    // 发送超时：30秒
            .callTimeout(180, TimeUnit.SECONDS)    // 整个调用超时：3分钟
            .build();
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");
    private static final Gson GSON = new Gson();

    private static final AudioConfig AUDIO_CONFIG = new AudioConfig(
            44100.0F, 16, 1, true, false
    );

    public static void main(String[] args) {
        try {
            LocalDateTime timeStart = LocalDateTime.now();
            Scanner scanner = new Scanner(System.in);
            JsonArray messagesHistory = new JsonArray(); // 对话历史存储

            while (true) {

                // 录音并检查有效性
                if (!audioRecorder()) {
                    continue;
                }

                // 语音转文字
                String userText = audioToText();
                System.out.println("识别结果: " + userText);

                // 检查终止条件
                if (userText.contains(END_WORD1) || userText.contains(END_WORD2) ) {
                    System.out.println("检测到结束语，程序即将退出...");
                    break;
                }

                if(userText.isBlank()){
                    System.out.println("录音识别结果为空，请重新开始说话");
                    continue;
                }

                // AI对话处理
                // 创建用户消息对象
                JsonObject userMessage = new JsonObject();
                userMessage.addProperty("role", "user");
                userMessage.addProperty("content", userText);

                // 调用AI对话（传入当前消息和历史）
                String aiResponse = aiTalk(userMessage, messagesHistory).replaceAll("\\(.*?\\)", "")
                        .replaceAll("\\（.*?\\）", "");
                System.out.println("语音回复: " + aiResponse);

                // 创建并添加助理消息到历史
                JsonObject assistantMessage = new JsonObject();
                assistantMessage.addProperty("role", "assistant");
                assistantMessage.addProperty("content", aiResponse);
                messagesHistory.add(userMessage);
                messagesHistory.add(assistantMessage);

                //历史记录超过限制则删除最开始的一轮对话
                if(messagesHistory.size()>CHAT_COUNT_MAX){
                    messagesHistory.remove(0);
                    messagesHistory.remove(0);
                }

                // 文字转语音
                textToSpeech(aiResponse);
                playAudio();

                System.out.println("\n=== 开始新的一轮对话 ===");
            }

            System.out.println("总用时: " + Duration.between(timeStart, LocalDateTime.now()).getSeconds() + "秒");
            scanner.close();
        } catch (Exception e) {
            System.err.println("主流程异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    static boolean audioRecorder() {
        try {
            TargetDataLine line = AudioSystem.getTargetDataLine(AUDIO_CONFIG.toAudioFormat());
            line.open(AUDIO_CONFIG.toAudioFormat());
            line.start();

            System.out.println("录音开始");

            ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192]; //约90ms检测一次
            int bytesRead;

            long lastSoundTime = System.currentTimeMillis();
            long startTime = lastSoundTime;
            boolean hasValidAudio = false;

            while (true) {
                bytesRead = line.read(buffer, 0, buffer.length);
                if (bytesRead > 0) {
                    audioBuffer.write(buffer, 0, bytesRead);

                    double currentVolume = calculateVolume(buffer, bytesRead);

                    // 音量检测逻辑
                    if (currentVolume > VOLUME_THRESHOLD) {
                        lastSoundTime = System.currentTimeMillis();
                        if (!hasValidAudio) {
                            hasValidAudio = true;
                        }
                    }

                    // 最大录音时间检查（始终有效）
                    if (!hasValidAudio && (System.currentTimeMillis() - startTime > MAX_RECORD_TIME)) {
                        System.out.println("达到最大录音时间，停止录音");
                        break;
                    }

                    // 仅在检测到有效语音后检查静音时长
                    if (hasValidAudio) {
                        if (System.currentTimeMillis() - lastSoundTime > SILENCE_DURATION) {
                            // System.out.println("静音超时，停止录音");
                            break;
                        }
                    }
                }
            }

            line.stop();
            line.close();

            // 有效性最终检查
            if (!hasValidAudio) {
                System.out.println("全程未检测到有效语音");
                audioBuffer.reset(); // 清空无效音频缓存
                return false;
            }

            // 保存有效录音
            try (AudioInputStream ais = new AudioInputStream(
                    new ByteArrayInputStream(audioBuffer.toByteArray()),
                    AUDIO_CONFIG.toAudioFormat(),
                    audioBuffer.size() / AUDIO_CONFIG.toAudioFormat().getFrameSize())) {

                AudioSystem.write(ais, AudioFileFormat.Type.WAVE, AUDIO_PATH.toFile());
                System.out.println("有效录音已保存至：" + AUDIO_PATH.toAbsolutePath());
                return true;
            }
        } catch (LineUnavailableException | IOException e) {
            throw new RuntimeException("录音失败", e);
        }
    }

    // 音量计算方法(MAV)
    private static double calculateVolume(byte[] audioData, int length) {
        long sum = 0;
        // 跳跃采样（每隔4个点采样一次）
        for (int i = 0; i < length; i += 8) { // 16位采样每个点占2字节，跳4点=8字节
            int lo = audioData[i] & 0xFF;
            int hi = audioData[i + 1] << 8;
            sum += Math.abs((short)(hi | lo));
        }
        return sum / (length / 8.0); // 计算跳跃采样后的平均值
    }


    static String audioToText() {
        try {
            RequestBody body = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", "recording.wav",
                            RequestBody.create(AUDIO_PATH.toFile(), MediaType.get("audio/wav")))
                    .addFormDataPart("model", "iic/SenseVoiceSmall")
                    .build();

            Request request = new Request.Builder()
                    .url(STT_API_URL)
                    .header("Authorization", "Bearer " + API_KEY)
                    .post(body)
                    .build();

            try (Response response = OK_HTTP_CLIENT_AI_CHAT.newCall(request).execute()) {
                if (!response.isSuccessful()) throw new IOException("STT请求失败: " + response.code());
                Map<?, ?> result = GSON.fromJson(response.body().charStream(), Map.class);
                return ((String) result.get("text")).trim();
            }
        } catch (IOException e) {
            throw new RuntimeException("语音转文本失败", e);
        }
    }

    static String aiTalk(JsonObject currentUserMessage, JsonArray messagesHistory) {
        try {
            System.out.println("等待回复中...");
            JsonObject requestBody = buildRequestBody(currentUserMessage, messagesHistory);
            String responseContent = executeAiRequest(requestBody);
            return parseAiResponse(responseContent);
        } catch (IOException e) {
            throw new RuntimeException("AI对话服务通信失败", e);
        }
    }

    static void textToSpeech(String aiResponse) {
        try {

            String jsonRequest = GSON.toJson(Map.of(
                    "model", "FunAudioLLM/CosyVoice2-0.5B",
                    "voice", VOICE_URL,
                    "input", aiResponse,
                    "response_format", "wav",
                    "sample_rate", 44100, // 确保采样率匹配
                    "stream", false
            ));

            Request request = new Request.Builder()
                    .url(TTS_API_URL)
                    .header("Authorization", "Bearer " + API_KEY)
                    .post(RequestBody.create(jsonRequest, JSON_MEDIA_TYPE))
                    .build();

            try (Response response = OK_HTTP_CLIENT_AI_CHAT.newCall(request).execute()) {
                if (!response.isSuccessful()) throw new IOException("TTS请求失败: " + response.code());
                try (InputStream is = response.body().byteStream()) {
                    Files.copy(is, OUTPUT_PATH, StandardCopyOption.REPLACE_EXISTING);
                }
                System.out.println("音频文件已保存至：" + OUTPUT_PATH.toAbsolutePath());
            }
        } catch (IOException e) {
            throw new RuntimeException("文本转语音失败", e);
        }
    }

    static void playAudio() {

        SourceDataLine line = null;
        AudioInputStream stream = null;
        try {
            File audioFile = OUTPUT_PATH.toFile();
            if (!audioFile.exists()) {
                System.err.println("语音文件不存在: " + OUTPUT_PATH);
                return;
            }
            long fileSize = audioFile.length();
            long maxDuration = 3600 * 44100 * 2; // 1小时音频的字节数
            if (fileSize > maxDuration) {
                throw new IllegalStateException("音频文件异常过长：" + fileSize + "字节");
            }
            // 获取音频流并确保格式正确
            stream = AudioSystem.getAudioInputStream(audioFile);
            AudioFormat format = stream.getFormat();

            // 验证音频格式参数
            if (format.getFrameSize() <= 0) {
                throw new IllegalArgumentException("无效的帧大小");
            }

            // 配置数据线
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format);
            line.start();

            // 使用缓冲区流式传输
            int bufferSize = 4096; // 可根据需要调整
            byte[] buffer = new byte[bufferSize];
            int bytesRead;

            while ((bytesRead = stream.read(buffer)) != -1) {
                int remaining = bytesRead;
                while (remaining > 0) {
                    int written = line.write(buffer, 0, remaining);
                    remaining -= written;
                }
            }

            // 等待播放完成
            line.drain();
        } catch (UnsupportedAudioFileException | IOException
                 | LineUnavailableException e) {
            System.err.println("播放失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // 安全释放资源
            if (line != null) {
                line.close();
            }
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // 构建请求体
    private static JsonObject buildRequestBody(JsonObject currentUserMessage, JsonArray messagesHistory) {
        // 创建系统消息
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", CONTEXT);

        // 构建完整消息列表
        JsonArray messages = new JsonArray();
        messages.add(systemMessage);

        // 添加历史消息
        for (JsonElement elem : messagesHistory) {
            messages.add(elem.deepCopy()); // 深拷贝避免引用问题
        }

        // 添加当前用户消息
        messages.add(currentUserMessage);

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", "Pro/deepseek-ai/DeepSeek-R1");
        requestBody.add("messages", messages);

        return requestBody;
    }

    // 创建并执行请求
    private static String executeAiRequest(JsonObject requestBody) throws IOException {
        Request request = new Request.Builder()
                .url(AID_API_URL)
                .header("Authorization", "Bearer " + API_KEY)
                .post(RequestBody.create(GSON.toJson(requestBody), JSON_MEDIA_TYPE))
                .build();

        try (Response response = OK_HTTP_CLIENT_AI_CHAT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ?
                        response.body().string() : "无响应内容";
                throw new IOException("AI服务请求失败 - 状态码: " + response.code()
                        + ", 错误信息: " + errorBody);
            }

            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                throw new IOException("收到空响应体");
            }
            return responseBody.string();
        }
    }

    // 解析并返回结果
    private static String parseAiResponse(String responseContent) {
        JsonObject root = GSON.fromJson(responseContent, JsonObject.class);

        // 验证响应结构
        if (!root.has("choices")) {
            throw new IllegalStateException("响应缺少choices字段");
        }

        JsonArray choices = root.getAsJsonArray("choices");
        if (choices.isEmpty()) {
            throw new IllegalStateException("choices数组为空");
        }

        JsonObject firstChoice = choices.get(0).getAsJsonObject();
        if (!firstChoice.has("message")) {
            throw new IllegalStateException("choice缺少message字段");
        }

        JsonObject message = firstChoice.getAsJsonObject("message");
        if (!message.has("content")) {
            throw new IllegalStateException("message缺少content字段");
        }
        // TODO:是否打印思考过程
        if(IS_REASONING_CONTENT_PRINT) {
            System.out.println(message.get("reasoning_content").getAsString()+"\n");
        }
        return message.get("content").getAsString();
    }

    // 音频配置封装类
    private record AudioConfig(
            float sampleRate,
            int sampleSize,
            int channels,
            boolean signed,
            boolean bigEndian
    ) {
        AudioFormat toAudioFormat() {
            return new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    sampleRate,
                    sampleSize,
                    channels,
                    (sampleSize / 8) * channels,
                    sampleRate,
                    bigEndian
            );
        }
    }
}
