## Setup Instructions for Firebase

1. Clone the repository:
   git clone https://github.com/your-username/CarbonWise.git

2. Navigate to the `app/` directory.

3. Make a copy of `google-services.json.example` and rename it to `google-services.json`:
    - On Linux/Mac: `cp app/google-services.json.example app/google-services.json`
    - On Windows: `copy app\google-services.json.example app\google-services.json`

4. Replace `"YOUR_API_KEY_HERE"` inside `app/google-services.json` with your team's API key (or your custom Firebase config).

5. Build and run the app in Android Studio.