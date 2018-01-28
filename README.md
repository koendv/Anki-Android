
This is Anki with Tones. Anki flashcards, with the following additional features for studying Chinese tones:

- graphs of your tones, drawn as you speak 
- recording and replaying your speech

This only runs on Android, sorry.

Downloads
---------
This is beta software. Use at your own risk.

Pre-compiled .apk for Android : [AnkiDroid.apk](http://www.kdvelectronics.eu/anki_with_tones/AnkiDroid-debug.apk)

Sample Anki deck to practice Mandarin Chinese tones:  [Mandarin Tone Pair Drills](http://www.kdvelectronics.eu/anki_with_tones/Mandarin%20Tone%20Pair%20Drills.zip)

Usage
-----

![Screenshot](https://raw.githubusercontent.com/koendv/Anki-Android/hotfix-2.8.3/docs/nanshuo.png)

When you review the deck, your screen should be divided in two halves. 

The bottom half is the reference pronounciation and its tones. This half has a small black triangle. The black triangle is the 'Play' button. Touch it and you should see the tones being drawn as the sound is played.

The top half is for your own speech. This half has a small red dot, a small black square and a small black triangle. The red dot is the 'Record' button; the black square is the 'Stop' recording button, and the black triangle is the 'Play' button. Touch the 'Record' button and say the word(s) on the flashcard out loud. You should see the tones being drawn as you speak. The recording ends when you stop speaking, or when you push the 'Stop' button. Hit the 'Play' button to listen to your voice.

Adapting a deck for use with 'Anki with Tones'
----------------------------------------------

You need an Anki a card deck with sound, either as .WAV or as .MP3 files. 
Other sound file formats may also work; but the main point is that it has to be sound *recordings*, not Text-To-Speech.

In your desktop Anki, edit the card template. If a card contains audio, just put `[pitch]` before the audio field. 
Example: if your audio field is `{{sound}}` edit card front and back template, and change 
`{{sound}}`
to 
`[pitch]
{{sound}}`

After editing, synchronize your deck to upload the changes to Ankiweb. Then go to your Android device and synchronize to download the changes from Ankiweb. That is all.

FAQ
---

Q. Can't install the binary.

A. You may have to uninstall Anki first. Also, check Settings -> Security -> Unknown sources is allowed.

Q. Recording sound does not work.

A. In Android, open Settings -> Apps -> AnkiDroid -> Permissions. Check all permissions have been granted, especially 'Microphone'. There may be some phones where recording or playback does not work; sorry. 

Sources
-------

The sources are in the [hotfix-2.8.3 branch](https://github.com/koendv/Anki-Android/tree/hotfix-2.8.3).

Todo
----
Karaoke mode.

