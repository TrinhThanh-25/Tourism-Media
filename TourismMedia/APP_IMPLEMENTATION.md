# Tourism Media Android implementation

## Runtime modes

- Account/password login and registration call the Express server at `http://10.0.2.2:3000/`.
- Google and Facebook buttons intentionally create a local demo session. Production OAuth requires Firebase and provider credentials.
- Content screens fall back to `SampleData` when the local server is unavailable.

## Main destinations

- Home: personalized greeting, travel hero and featured locations.
- Explore: live search, location details, favorite and check-in actions.
- Trips: community trips, trip details, favorites and draft creation.
- Challenges: progress, challenge joining, points, reward catalog and redemption.
- Profile: account details, points, profile editing and logout.

## Local server

Run `Travel-App-Server` on port 3000. Android Emulator maps the host through `10.0.2.2`; a physical device requires changing `ApiClient.BASE_URL` to the computer's LAN address.
