package com.unpal.app.data

import com.google.gson.annotations.SerializedName

/**
 * Instagram 데이터 내보내기 JSON 구조를 위한 데이터 클래스들
 */

/**
 * 사용자 정보를 담는 기본 데이터 클래스
 * @property value 사용자 이름 (Instagram username)
 * @property timestamp 팔로우한 시간 (Unix timestamp)
 */
data class StringListData(
    @SerializedName("value")
    val value: String,
    @SerializedName("timestamp")
    val timestamp: Long
)

/**
 * 팔로워/팔로잉 관계 데이터
 * @property stringListData 사용자 정보 리스트
 */
data class RelationshipData(
    @SerializedName("string_list_data")
    val stringListData: List<StringListData>
)

/**
 * followers.json 파일 구조
 * @property relationshipsFollowers 팔로워 목록
 */
data class FollowersJson(
    @SerializedName("relationships_followers")
    val relationshipsFollowers: List<RelationshipData>
)

/**
 * following.json 파일 구조
 * @property relationshipsFollowing 팔로잉 목록
 */
data class FollowingJson(
    @SerializedName("relationships_following")
    val relationshipsFollowing: List<RelationshipData>
)

/**
 * 사용자 정보를 담는 간소화된 데이터 클래스
 * @property username 사용자 이름
 * @property timestamp 팔로우한 시간
 */
data class InstagramUser(
    val username: String,
    val timestamp: Long
) {
    /**
     * timestamp를 읽기 쉬운 날짜 형식으로 변환
     */
    fun getFormattedDate(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp * 1000))
    }
}

/**
 * 분석 결과를 담는 데이터 클래스
 * @property followers 나를 팔로우하는 사용자 목록
 * @property following 내가 팔로우하는 사용자 목록
 * @property notFollowingBack 맞팔이 아닌 사용자 목록 (내가 팔로우하지만 나를 팔로우하지 않는)
 * @property notFollowedBack 나를 팔로우하지만 내가 팔로우하지 않는 사용자 목록
 */
data class AnalysisResult(
    val followers: List<InstagramUser>,
    val following: List<InstagramUser>,
    val notFollowingBack: List<InstagramUser>,
    val notFollowedBack: List<InstagramUser>
) {
    val followersCount: Int get() = followers.size
    val followingCount: Int get() = following.size
    val notFollowingBackCount: Int get() = notFollowingBack.size
    val notFollowedBackCount: Int get() = notFollowedBack.size
    val mutualCount: Int get() = followersCount - notFollowedBackCount
}
