package com.example.miniblog.network

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object NetworkClient {

    private const val BASE_URL = "https://jsonplaceholder.typicode.com"

    fun get(endpoint: String): NetworkResult<String> {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(BASE_URL + endpoint)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.connect()

            val code = connection.responseCode
            if (code in 200..299) {
                NetworkResult.Success(readStream(connection.inputStream))
            } else {
                NetworkResult.Error("Server returned HTTP $code")
            }
        } catch (e: Exception) {
            NetworkResult.Error("Network error: ${e.message}")
        } finally {
            connection?.disconnect()
        }
    }

    fun post(endpoint: String, jsonBody: String): NetworkResult<String> {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(BASE_URL + endpoint)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.doOutput = true
            connection.setRequestProperty(
                "Content-Type", "application/json; charset=UTF-8"
            )

            connection.outputStream.use { os ->
                os.write(jsonBody.toByteArray(Charsets.UTF_8))
            }

            val code = connection.responseCode
            val stream = if (code in 200..299)
                connection.inputStream else connection.errorStream
            val body = readStream(stream)

            if (code in 200..299) NetworkResult.Success(body)
            else NetworkResult.Error("Server returned HTTP $code")
        } catch (e: Exception) {
            NetworkResult.Error("Network error: ${e.message}")
        } finally {
            connection?.disconnect()
        }
    }

    private fun readStream(input: InputStream): String {
        val reader = BufferedReader(InputStreamReader(input))
        val builder = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            builder.append(line)
        }
        reader.close()
        return builder.toString()
    }
}
