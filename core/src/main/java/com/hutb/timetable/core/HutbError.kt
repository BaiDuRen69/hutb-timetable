package com.hutb.timetable.core

/**
 * 统一错误类型，界面层据此给出可读提示。
 */
sealed class HutbError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** 未登录或会话过期 —— 需要重新走登录流程。 */
    class NotLoggedIn(cause: Throwable? = null) :
        HutbError("登录已失效，请重新登录", cause)

    /** 网络不可达 / 超时。 */
    class Network(cause: Throwable? = null) :
        HutbError("网络连接失败，请检查网络后重试", cause)

    /** 服务器返回业务错误。 */
    class Server(val code: String, message: String) : HutbError(message)

    /** 页面结构与预期不符（解析失败）。 */
    class Parse(message: String, cause: Throwable? = null) :
        HutbError("教务系统页面结构已变化，解析失败：$message", cause)

    /** 其他未归类错误。 */
    class Unknown(message: String, cause: Throwable? = null) :
        HutbError(message, cause)
}
