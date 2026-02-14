package com.unpal.app.data

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Instagram JSON 파일을 파싱하는 클래스
 */
class InstagramParser {

    private val gson = Gson()

    /**
     * followers.json 문자열을 파싱하여 InstagramUser 리스트로 변환
     * @param jsonString followers.json 파일의 내용
     * @return 팔로워 목록
     * @throws InstagramParseException 파싱 실패 시
     */
    fun parseFollowers(jsonString: String): List<InstagramUser> {
        return try {
            val followersJson = gson.fromJson(jsonString, FollowersJson::class.java)
            followersJson.relationshipsFollowers.flatMap { relationshipData ->
                relationshipData.stringListData.map { data ->
                    InstagramUser(
                        username = data.value,
                        timestamp = data.timestamp
                    )
                }
            }
        } catch (e: JsonSyntaxException) {
            throw InstagramParseException("followers.json 파싱 실패: ${e.message}", e)
        } catch (e: NullPointerException) {
            throw InstagramParseException("followers.json 형식이 올바르지 않습니다", e)
        }
    }

    /**
     * following.json 문자열을 파싱하여 InstagramUser 리스트로 변환
     * @param jsonString following.json 파일의 내용
     * @return 팔로잉 목록
     * @throws InstagramParseException 파싱 실패 시
     */
    fun parseFollowing(jsonString: String): List<InstagramUser> {
        return try {
            val followingJson = gson.fromJson(jsonString, FollowingJson::class.java)
            followingJson.relationshipsFollowing.flatMap { relationshipData ->
                relationshipData.stringListData.map { data ->
                    InstagramUser(
                        username = data.value,
                        timestamp = data.timestamp
                    )
                }
            }
        } catch (e: JsonSyntaxException) {
            throw InstagramParseException("following.json 파싱 실패: ${e.message}", e)
        } catch (e: NullPointerException) {
            throw InstagramParseException("following.json 형식이 올바르지 않습니다", e)
        }
    }

    /**
     * InputStream에서 followers.json을 파싱
     * @param inputStream followers.json 파일의 InputStream
     * @return 팔로워 목록
     */
    fun parseFollowers(inputStream: InputStream): List<InstagramUser> {
        return try {
            val reader = InputStreamReader(inputStream, Charsets.UTF_8)
            val jsonString = reader.readText()
            reader.close()
            parseFollowers(jsonString)
        } catch (e: InstagramParseException) {
            throw e
        } catch (e: Exception) {
            throw InstagramParseException("followers.json 읽기 실패: ${e.message}", e)
        }
    }

    /**
     * InputStream에서 following.json을 파싱
     * @param inputStream following.json 파일의 InputStream
     * @return 팔로잉 목록
     */
    fun parseFollowing(inputStream: InputStream): List<InstagramUser> {
        return try {
            val reader = InputStreamReader(inputStream, Charsets.UTF_8)
            val jsonString = reader.readText()
            reader.close()
            parseFollowing(jsonString)
        } catch (e: InstagramParseException) {
            throw e
        } catch (e: Exception) {
            throw InstagramParseException("following.json 읽기 실패: ${e.message}", e)
        }
    }

    companion object {
        /**
         * 싱글톤 인스턴스
         */
        val instance: InstagramParser by lazy { InstagramParser() }
    }
}

/**
 * Instagram 파싱 관련 예외
 */
class InstagramParseException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
