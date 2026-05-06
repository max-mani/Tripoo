import { initializeApp, type FirebaseApp } from 'firebase/app'
import { getAuth } from 'firebase/auth'
import { getFirestore } from 'firebase/firestore'

function requiredEnv(name: string): string {
  const v = import.meta.env[name]
  if (!v || String(v).trim() === '') {
    throw new Error(
      `Missing ${name}. Add Firebase web config to Netlify or frontend/.env.local — see .env.example`,
    )
  }
  return String(v)
}

const firebaseConfig: Record<string, string> = {
  apiKey: requiredEnv('VITE_FIREBASE_API_KEY'),
  authDomain: requiredEnv('VITE_FIREBASE_AUTH_DOMAIN'),
  projectId: requiredEnv('VITE_FIREBASE_PROJECT_ID'),
  storageBucket: requiredEnv('VITE_FIREBASE_STORAGE_BUCKET'),
  messagingSenderId: requiredEnv('VITE_FIREBASE_MESSAGING_SENDER_ID'),
  appId: requiredEnv('VITE_FIREBASE_APP_ID'),
}
const measure = import.meta.env.VITE_FIREBASE_MEASUREMENT_ID
if (measure && String(measure).trim()) {
  firebaseConfig.measurementId = String(measure)
}

let app: FirebaseApp
try {
  app = initializeApp(firebaseConfig)
} catch (e) {
  console.error('Firebase init failed', e)
  throw e
}

export const auth = getAuth(app)
export const db = getFirestore(app)
