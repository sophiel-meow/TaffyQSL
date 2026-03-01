package moe.zzy040330.taffyqsl.data.lotw

sealed class LotwException : Exception() {
    /** Credentials not stored on device */
    class NoCredentials : LotwException()

    /** Server rejected login (username/password incorrect) */
    class AuthFailed : LotwException()

    /** Server returned a non-ADIF response for other reasons */
    class ServerError(val httpCode: Int) : LotwException()
}
