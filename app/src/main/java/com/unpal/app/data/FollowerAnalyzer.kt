package com.unpal.app.data

import java.io.InputStream

/**
 * Instagram 팔로워/팔로잉 관계를 분석하는 클래스
 */
class FollowerAnalyzer(
    private val parser: InstagramParser = InstagramParser.instance
) {

    /**
     * 팔로워와 팔로잉 목록을 분석하여 맞팔이 아닌 사용자 등을 찾음
     * @param followers 팔로워 목록
     * @param following 팔로잉 목록
     * @return 분석 결과
     */
    fun analyze(
        followers: List<InstagramUser>,
        following: List<InstagramUser>
    ): AnalysisResult {
        // 팔로워 username Set (빠른 검색을 위해)
        val followerUsernames = followers.map { it.username.lowercase() }.toSet()

        // 팔로잉 username Set (빠른 검색을 위해)
        val followingUsernames = following.map { it.username.lowercase() }.toSet()

        // 맞팔이 아닌 사용자: 내가 팔로우하지만 나를 팔로우하지 않는 사람
        val notFollowingBack = following.filter { user ->
            user.username.lowercase() !in followerUsernames
        }

        // 나를 팔로우하지만 내가 팔로우하지 않는 사용자
        val notFollowedBack = followers.filter { user ->
            user.username.lowercase() !in followingUsernames
        }

        return AnalysisResult(
            followers = followers,
            following = following,
            notFollowingBack = notFollowingBack,
            notFollowedBack = notFollowedBack
        )
    }

    /**
     * JSON 문자열에서 직접 분석 수행
     * @param followersJson followers.json 내용
     * @param followingJson following.json 내용
     * @return 분석 결과
     */
    fun analyzeFromJson(
        followersJson: String,
        followingJson: String
    ): AnalysisResult {
        val followers = parser.parseFollowers(followersJson)
        val following = parser.parseFollowing(followingJson)
        return analyze(followers, following)
    }

    /**
     * InputStream에서 직접 분석 수행
     * @param followersStream followers.json InputStream
     * @param followingStream following.json InputStream
     * @return 분석 결과
     */
    fun analyzeFromStreams(
        followersStream: InputStream,
        followingStream: InputStream
    ): AnalysisResult {
        val followers = parser.parseFollowers(followersStream)
        val following = parser.parseFollowing(followingStream)
        return analyze(followers, following)
    }

    /**
     * 맞팔이 아닌 사용자 목록만 반환 (간편 메서드)
     * @param followersJson followers.json 내용
     * @param followingJson following.json 내용
     * @return 맞팔이 아닌 사용자 목록
     */
    fun getNotFollowingBack(
        followersJson: String,
        followingJson: String
    ): List<InstagramUser> {
        return analyzeFromJson(followersJson, followingJson).notFollowingBack
    }

    /**
     * 맞팔이 아닌 사용자 목록을 timestamp 기준 오래된 순으로 정렬하여 반환
     * @param followersJson followers.json 내용
     * @param followingJson following.json 내용
     * @return 오래된 순으로 정렬된 맞팔이 아닌 사용자 목록
     */
    fun getNotFollowingBackSortedByOldest(
        followersJson: String,
        followingJson: String
    ): List<InstagramUser> {
        return getNotFollowingBack(followersJson, followingJson)
            .sortedBy { it.timestamp }
    }

    /**
     * 맞팔이 아닌 사용자 목록을 timestamp 기준 최신 순으로 정렬하여 반환
     * @param followersJson followers.json 내용
     * @param followingJson following.json 내용
     * @return 최신 순으로 정렬된 맞팔이 아닌 사용자 목록
     */
    fun getNotFollowingBackSortedByNewest(
        followersJson: String,
        followingJson: String
    ): List<InstagramUser> {
        return getNotFollowingBack(followersJson, followingJson)
            .sortedByDescending { it.timestamp }
    }

    /**
     * 맞팔이 아닌 사용자 username 목록만 반환
     * @param followersJson followers.json 내용
     * @param followingJson following.json 내용
     * @return 맞팔이 아닌 사용자 username 목록
     */
    fun getNotFollowingBackUsernames(
        followersJson: String,
        followingJson: String
    ): List<String> {
        return getNotFollowingBack(followersJson, followingJson)
            .map { it.username }
    }

    companion object {
        /**
         * 싱글톤 인스턴스
         */
        val instance: FollowerAnalyzer by lazy { FollowerAnalyzer() }
    }
}
