// llm_stream_buffer.hpp
// 把 LLM 解码输出接到回调:自定义 std::streambuf,收到文本即回调。
// 改编自官方 MNN 仓库 apps/Android/MnnLlmChat 同名文件(补全 <functional> 与 overflow)。
#pragma once

#include <functional>
#include <ostream>
#include <sstream>

class LlmStreamBuffer : public std::streambuf {
public:
    using Callback = std::function<void(const char* str, size_t len)>;

    explicit LlmStreamBuffer(Callback callback) : callback_(std::move(callback)) {}

protected:
    std::streamsize xsputn(const char* s, std::streamsize n) override {
        if (callback_) {
            callback_(s, static_cast<size_t>(n));
        }
        return n;
    }

    int_type overflow(int_type ch) override {
        if (ch != traits_type::eof() && callback_) {
            char c = traits_type::to_char_type(ch);
            callback_(&c, 1);
        }
        return traits_type::not_eof(ch);
    }

private:
    Callback callback_ = nullptr;
};
