# Tripoo - Group Trip Planner

A modern Android application for planning group trips with expense tracking, task management, and real-time collaboration.

## Features

- **Firebase Authentication**: Google Sign-In and Email/Password authentication
- **Trip Management**: Create trips with unique codes, join trips via code
- **Expense Tracking**: Track expenses, split costs among members, view who owes what
- **Task Management**: Organize tasks by category (Booking, Packing, General)
- **Real-time Updates**: Live synchronization using Firestore listeners
- **Material Design 3**: Modern, beautiful UI following Material Design guidelines

## Tech Stack

- **Language**: Java 17
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)
- **Architecture**: MVVM (Model-View-ViewModel)
- **Backend**: Firebase (Authentication, Firestore, Storage)
- **UI**: Material Design 3, Navigation Component, ViewBinding

## Project Structure

```
app/src/main/java/com/example/tripoo/
├── data/
│   ├── model/          # Data models (User, Trip, Expense, Task, TripMember)
│   └── repository/     # Repository classes for data access
├── ui/
│   ├── auth/          # Authentication fragments
│   ├── splash/        # Splash screen
│   ├── home/          # Home screen and trip creation/joining
│   ├── expense/        # Expense tracking
│   ├── tasks/          # Task management
│   ├── groups/         # Group/member management
│   └── profile/         # User profile
├── viewmodel/          # ViewModels for each feature
├── utils/              # Utility classes
└── MainActivity.java   # Single Activity with Navigation Component
```

## Setup Instructions

### Prerequisites

- Android Studio Hedgehog or newer
- JDK 17 or higher
- Firebase account

### Firebase Configuration

1. **Create Firebase Project**
   - Go to [Firebase Console](https://console.firebase.google.com/)
   - Create a new project named "Tripoo"

2. **Add Android App**
   - Click "Add app" → Select Android
   - Package name: `com.example.tripoo`
   - Download `google-services.json`
   - Place it in `app/` directory (already included)

3. **Enable Firebase Services**
   - **Authentication**: 
     - Go to Authentication → Sign-in method
     - Enable "Email/Password"
     - Enable "Google" (configure OAuth consent screen)
   - **Firestore Database**:
     - Go to Firestore Database → Create database
     - Start in test mode (or use provided security rules)
   - **Storage** (optional, for profile images):
     - Go to Storage → Get started
     - Start in test mode

4. **Configure Google Sign-In**
   - In Firebase Console → Authentication → Sign-in method → Google
   - Add SHA-1 fingerprint:
     ```bash
     keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
     ```
   - Copy the SHA-1 fingerprint and add it to Firebase Console

5. **Deploy Firestore Security Rules**
   - Go to Firestore Database → Rules
   - Copy contents from `firestore.rules`
   - Paste and publish

### Build and Run

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd Tripoo
   ```

2. **Sync Gradle**
   - Open project in Android Studio
   - Wait for Gradle sync to complete

3. **Build the project**
   ```bash
   ./gradlew clean
   ./gradlew build
   ```

4. **Run on device/emulator**
   - Connect Android device or start emulator (API 24+)
   - Click "Run" in Android Studio or:
   ```bash
   ./gradlew installDebug
   ```

## Architecture

### MVVM Pattern

- **Model**: Data classes and Firestore models
- **View**: Fragments with ViewBinding
- **ViewModel**: Business logic, LiveData observation
- **Repository**: Data access layer, Firestore operations

### Single Activity Architecture

- One `MainActivity` hosts all fragments
- Navigation Component handles fragment transitions
- Bottom Navigation for main screens (Home, Expense, Tasks, Groups)

### Real-time Updates

- Firestore snapshot listeners in repositories
- LiveData in ViewModels for UI updates
- Automatic synchronization across devices

## Firestore Structure

### Collections

```
users/
  {userId}/
    name: string
    email: string
    photoUrl: string
    activeTripId: string

trips/
  {tripId}/
    place: string
    startDate: timestamp
    endDate: timestamp
    budget: number
    tripCode: string (format: TRP-XXX)
    adminId: string
    isActive: boolean
    
    members/
      {userId}/
        userId: string
        name: string
        email: string
        photoUrl: string
        isAdmin: boolean
    
    expenses/
      {expenseId}/
        title: string
        amount: number
        paidBy: string (userId)
        splitWith: array<string> (userIds)
        createdBy: string (userId)
        timestamp: timestamp
    
    tasks/
      {taskId}/
        title: string
        category: string (Booking/Packing/General)
        assignedTo: string (userId)
        completed: boolean
        createdBy: string (userId)
        dueDate: timestamp
```

## Key Features Implementation

### Trip Code Generation
- Format: `TRP-XXX` where XXX is 3-digit number (100-999)
- Uniqueness checked against active trips
- Codes become invalid after trip end date
- Can be reused for new trips

### Expense Splitting
- Calculate "You Owe" and "You Are Owed" amounts
- Split expenses equally among selected members
- Real-time updates when expenses are added/modified

### Task Categories
- **Booking**: Flight, hotel, car rental, etc.
- **Packing**: Items to pack, checklist
- **General**: Other trip-related tasks

### Permissions
- **Admin**: Can edit/delete any expense or task
- **Creator**: Can edit/delete own expenses/tasks
- **Member**: Can add expenses/tasks, view all data

## Security Rules

Firestore security rules ensure:
- Users can only access their own user document
- Only trip members can read trip data
- Only admin/creator can edit expenses and tasks
- Members can add expenses/tasks but only edit their own

See `firestore.rules` for complete rules.

## Troubleshooting

### Build Errors
- Ensure `google-services.json` is in `app/` directory
- Check that all dependencies are synced in Gradle
- Verify Java 17 is configured

### Authentication Issues
- Check SHA-1 fingerprint is added to Firebase
- Verify Google Sign-In is enabled in Firebase Console
- Ensure OAuth consent screen is configured

### Firestore Permission Errors
- Deploy security rules from `firestore.rules`
- Check that user is authenticated
- Verify user is a member of the trip

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a pull request

## License

This project is licensed under the MIT License.

## Acknowledgments

- Material Design 3 components
- Firebase for backend services
- Android Jetpack libraries
