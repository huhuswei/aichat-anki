package com.ss.aianki;

import com.theokanning.openai.completion.chat.ChatCompletionRequest;

import okhttp3.Call;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Streaming;
import io.reactivex.Flowable;

public interface OpenAiApi {
    @Streaming
    @POST("v1/chat/completions")
    Flowable<ResponseBody> streamChatCompletion(@Body ChatCompletionRequest request);

    @Streaming
    @POST("v1/chat/completions")
    Flowable<ResponseBody> streamChatCompletionRaw(@Body RequestBody request);

    // 同步版本，用于获取 Call 对象以便取消
    @POST("v1/chat/completions")
    Call streamChatCompletionSync(@Body ChatCompletionRequest request);
} 