import { BrowserRouter } from 'react-router-dom';
import { AuthProvider } from '@/lib/auth/AuthProvider';
import { AppRouter } from './routes/router';

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <AppRouter />
      </BrowserRouter>
    </AuthProvider>
  );
}
