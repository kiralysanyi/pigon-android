# Pigon (Android)

Native android app for pigon

## Tech

Programming language: `Kotlin`

### Requirements

- Android 13 or above
- Some newer Android Studio (ancient versions not supported)

### Libraries

- `okhttp` for http requests
- `socket-io-clinet` for interacting with the socketio backend
- `exoplayer` for playing videos
- `firebase-messaging-ktx` for push notifications and some other stuff
- And a bunch of other libraries needed for building the app, the list is very long

### APIHandler

The APIHandler object is responsible for interacting with the backend, it is a huge object because it lacks a bunch of optimisations.

The APIHandler also lacks proper error handling, but it's not a problem because pigon always works! (except when it doesn't 🤣)

## Features

### UI

#### Dynamic theme

For some reason that is a big deal in 2025, I am looking at you MÁV 👀

### Chat

You can send/receive messages, that's the point of the project.

It supports image/video sending too.

#### Managing chats

You can start new chats, create/delete groups

### Calls

Pigon android has the better implementation of the calling stuff, this one actually works, it's pure webrtc, so it was a pain to implement, but it works most of the time.

Calls in pigon android support audio and video

### Authentication

Basically the same as on the web, but you don't have to provide a devicename because it's automatically generated from device specs.

#### Passkeys

Passkey support is fully integrated in the app, no janky web sso stuff.

Note: you have to set up assettlinks.json properly for this to work. More info about this: [Android Credential Manager](https://developer.android.com/identity/sign-in/credential-manager)

### Push notifications

Push notifications is provided by firebase FCM, you have to configure it on the backend and here too.

More info about configuring FCM in an android project: [Add firebase to your Android project](https://firebase.google.com/docs/android/setup)



