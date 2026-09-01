<div align="center">
  <img src="appicon.png" alt="Mini Blog Explorer" width="100">
</div>

<div align="center"> Mini Blog Explorer </div>

<br>
A clean, modern Android blog reader app built with Kotlin, Material Design 3, and coroutines.

Fetches posts from [JSONPlaceholder](https://jsonplaceholder.typicode.com) API, displays them in a card-based list, lets you view comments on each post, and create new posts.

---

## Features

| Feature | Description |
|---------|-------------|
| Post List | Browse 100 posts in a scrollable Material card list |
| Search / Filter | Real-time search across post titles and bodies |
| Comments | Tap any post to view its comments with author email |
| Create Post | Publish new posts via form with validation |
| Pull to Refresh | Swipe down to reload the post list |
| Dark Mode | Full dark theme support via system settings |
| Offline Handling | Retry button when no internet connection |
| State Preservation | Data survives screen rotation |
| Material 3 UI | Modern design with cards, FAB, toolbars, and animations |

---

## Getting Started

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 11+

### Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/Meetduggar23/MiniBlog.git
   ```
2. **Open in Android Studio**
   - File > Open > select the cloned folder
3. **Sync Gradle**
   - Wait for the initial Gradle sync to finish
4. **Run the app**
   - Select an emulator or connected device
   - Click Run

---

## Screenshots

> Add screenshots here after testing on a device/emulator.

| Post List | Post Detail | Create Post |
|-----------|-------------|-------------|
| ![List](screenshots/list.png) | ![Detail](screenshots/detail.png) | ![Create](screenshots/create.png) |

---

## What to Test

### Basic Flow

1. **Launch the app** - Posts load from JSONPlaceholder API
2. **Tap any post** - Detail screen opens with title, body, and comments
3. **Press back** - Returns to post list
4. **Tap the + FAB** - Create Post screen opens

### Search

5. **Type in the search bar** - Posts filter in real-time
6. **Type gibberish** - Shows "No posts match" empty state
7. **Clear search** - All posts reappear

### Create Post

8. **Submit empty form** - Inline validation errors appear
9. **Fill title + body, tap Publish** - Success card with server response
10. **Submit with no network** - Error card appears in red

### Edge Cases

11. **Turn off WiFi/mobile data** - "No internet connection" + Retry button
12. **Rotate the device** - Posts/comments survive the rotation
13. **Pull down on the list** - SwipeRefreshLayout triggers reload
14. **Long list** - Smooth scrolling with proper card elevation

---

## Project Structure (Layouts)

| Layout | Used By |
|--------|---------|
| `activity_main.xml` | MainActivity - toolbar, search bar, post list, FAB |
| `item_post.xml` | PostAdapter - individual post card |
| `activity_post_detail.xml` | PostDetailActivity - body card + comments list |
| `item_comment.xml` | CommentAdapter - individual comment card |
| `activity_create_post.xml` | CreatePostActivity - title/body form + result cards |

---

## License

This project is for educational purposes.
