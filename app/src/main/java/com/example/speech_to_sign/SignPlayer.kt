package com.example.speech_to_sign

import android.content.Context
import android.net.Uri
import android.widget.VideoView
import java.util.LinkedList
import java.util.Locale

class SignPlayer(private val context: Context, private val videoView: VideoView) {

    // Put videos to play
    private val videoQueue = LinkedList<Int>()
    private var isPlaying = false

    private val fileMap = mapOf(
        "do" to "action_do",
        "this" to "sign_this"
    )

    init {

        videoView.setOnCompletionListener {
            playNextVideo()
        }
    }

    fun playSentence(sentence: String): List<String> {
        //remove old vids
        videoQueue.clear()
        videoView.stopPlayback()

        val missingWords = mutableListOf<String>()

        //simple regex
        val words = sentence.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z ]"), "") // Remove ? ! . ,
            .split(" ")

        //scan for each word
        for (word in words) {
            if (word.isBlank()) continue
            var filename = word

            if (fileMap.containsKey(word)) {
                filename = fileMap[word]!!
            }

            if (filename.isNotEmpty() && filename[0].isDigit()) {
                filename = "n$filename"
            }


            val resId = context.resources.getIdentifier(filename, "raw", context.packageName)


            if (resId != 0) {
                videoQueue.add(resId)
            } else {
                var allLettersFound = true

                for (char in word) {

                    val letterResId = context.resources.getIdentifier(char.toString(), "raw", context.packageName)

                    if (letterResId != 0) {
                        videoQueue.add(letterResId)
                    } else {
                        allLettersFound = false
                    }
                }

            }
        }


        if (videoQueue.isNotEmpty()) {
            playNextVideo()
        }

        return missingWords
    }

    private fun playNextVideo() {
        if (videoQueue.isEmpty()) {
            isPlaying = false
            return
        }

        isPlaying = true
        val nextVideoId = videoQueue.poll() // Get and remove the next video ID

        val videoPath = "android.resource://${context.packageName}/$nextVideoId"
        videoView.setVideoURI(Uri.parse(videoPath))
        videoView.start()
    }
}