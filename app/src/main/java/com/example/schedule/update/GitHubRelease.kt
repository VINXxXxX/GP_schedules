package com.example.schedule.update

data class GitHubRelease(
    val tag_name: String,
    val body: String?,
    val assets: List<GitHubAsset>
)

data class GitHubAsset(
    val browser_download_url: String
)
