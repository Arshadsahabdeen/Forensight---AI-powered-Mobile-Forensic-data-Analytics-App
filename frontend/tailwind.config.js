/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        slatepanel: '#101820',
        ink: '#0b1117',
        steel: '#1b2a34',
        cyanline: '#46b3c7',
        signal: '#74a892'
      },
      boxShadow: {
        forensic: '0 18px 50px rgba(0, 0, 0, 0.32)'
      }
    }
  },
  plugins: []
};
