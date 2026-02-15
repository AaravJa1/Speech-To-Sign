package com.example.speech_to_sign

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.VideoView
import java.util.LinkedList
import java.util.Locale

class SignPlayer(
    private val context: Context,
    private val videoView: VideoView,
    private val placeholderImage: ImageView,
    private val tvResult: TextView // <--- NEW: We need this to update the text color!
) {

    // Helper class to store which video belongs to which word
    data class VideoItem(val resId: Int, val wordIndex: Int)

    private val videoQueue = LinkedList<VideoItem>()
    private var isPlaying = false
    private var currentSpannable: SpannableString? = null

    // Your dictionary
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
        videoQueue.clear()
        videoView.stopPlayback()

        val missingWords = mutableListOf<String>()

        // 1. Prepare the text for highlighting
        // We assume the sentence is what is displayed in tvResult
        currentSpannable = SpannableString(sentence)
        tvResult.text = currentSpannable

        // 2. Split sentence to find word positions
        // We use a regex to split but keep track of indices is tricky with split()
        // So we will just split by space for simplicity in this version.
        // NOTE: This assumes tvResult.text matches "sentence" exactly.

        val cleanSentence = sentence.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9 ]"), "")
        val words = cleanSentence.split(" ")

        // We need to map "clean words" back to the original sentence to find positions
        // But for the MVP, let's just highlight based on word count index.

        var currentWordIndex = 0

        for (i in words.indices) {
            val word = words[i]
            if (word.isBlank()) continue

            var filename = word
            if (fileMap.containsKey(word)) filename = fileMap[word]!!
            if (filename.isNotEmpty() && filename[0].isDigit()) filename = "n$filename"

            val resId = context.resources.getIdentifier(filename, "raw", context.packageName)

            if (resId != 0) {
                // Found a video for this word
                videoQueue.add(VideoItem(resId, i))
            } else {
                // Spelling it out (Letter by letter)
                missingWords.add(word)
                for (char in word) {
                    var charFilename = char.toString()
                    if (char.isDigit()) charFilename = "n$char"

                    val letterResId = context.resources.getIdentifier(charFilename, "raw", context.packageName)
                    if (letterResId != 0) {
                        // All letters for this word belong to the same wordIndex (i)
                        videoQueue.add(VideoItem(letterResId, i))
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
            videoView.visibility = View.GONE
            placeholderImage.visibility = View.VISIBLE

            // Reset text color (remove all highlights)
            resetHighlights()
            return
        }

        isPlaying = true
        placeholderImage.visibility = View.GONE
        videoView.visibility = View.VISIBLE

        val nextItem = videoQueue.poll() // Get the VideoItem object

        // HIGHLIGHT THE WORD!
        highlightWord(nextItem.wordIndex)

        // Play the video
        val videoPath = "android.resource://${context.packageName}/${nextItem.resId}"
        videoView.setVideoURI(Uri.parse(videoPath))
        videoView.start()
    }

    private fun highlightWord(wordIndex: Int) {
        val text = tvResult.text.toString()
        val words = text.split(" ") // Assumes single spaces between words

        if (wordIndex >= words.size) return

        // Calculate start and end positions of the word
        var startIndex = 0
        for (i in 0 until wordIndex) {
            startIndex += words[i].length + 1 // +1 for the space
        }
        val endIndex = startIndex + words[wordIndex].length

        // Apply Cyan color to the current word
        val spannable = SpannableString(text)


        val highlightColor = Color.parseColor("#03DAC5")

        spannable.setSpan(
            ForegroundColorSpan(highlightColor),
            startIndex,
            endIndex,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        tvResult.text = spannable
    }

    private fun resetHighlights() {
        // Just set the text back to plain white
        val text = tvResult.text.toString()
        tvResult.text = text
    }
}