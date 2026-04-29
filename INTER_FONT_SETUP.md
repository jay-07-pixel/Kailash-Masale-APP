# Inter Font Setup Instructions

To complete the Inter font setup for this Android app, you need to manually add the Inter font files.

## Steps:

1. **Download Inter Font Files:**
   - Visit: https://github.com/rsms/inter/releases
   - Download the latest release (usually a ZIP file)
   - Or visit: https://rsms.me/inter/ and download from there

2. **Extract and Copy Font Files:**
   - Extract the downloaded ZIP file
   - Navigate to the `fonts/` or `ttf/` folder in the extracted files
   - Copy the following TTF files to `app/src/main/res/font/`:
     - `Inter-Regular.ttf` → rename to `inter_regular.ttf`
     - `Inter-Medium.ttf` → rename to `inter_medium.ttf`
     - `Inter-SemiBold.ttf` → rename to `inter_semibold.ttf`
     - `Inter-Bold.ttf` → rename to `inter_bold.ttf`

3. **Verify Files:**
   - Make sure these 4 files exist in `app/src/main/res/font/`:
     - `inter_regular.ttf`
     - `inter_medium.ttf`
     - `inter_semibold.ttf`
     - `inter_bold.ttf`

4. **Rebuild the Project:**
   - Clean and rebuild the project in Android Studio
   - The Inter font will now be applied throughout the app

## Alternative: Direct Download Links

If you prefer direct download:
- Regular: https://github.com/rsms/inter/raw/master/docs/font-files/Inter-Regular.ttf
- Medium: https://github.com/rsms/inter/raw/master/docs/font-files/Inter-Medium.ttf
- SemiBold: https://github.com/rsms/inter/raw/master/docs/font-files/Inter-SemiBold.ttf
- Bold: https://github.com/rsms/inter/raw/master/docs/font-files/Inter-Bold.ttf

Download each file and save them in `app/src/main/res/font/` with the lowercase names as shown above.

