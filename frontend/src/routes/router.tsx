import { Navigate, Route, Routes } from 'react-router-dom';
import { ProtectedRoute } from '@/components/layout';
import { useAuth } from '@/lib/auth/useAuth';
import { CustomerDetailPage, CustomerSearchPage, LoginPage, NotFoundPage, StyleguidePage } from '@/pages';

/** "/" has no page of its own — it just resolves where a session should land. */
function RootRedirect() {
  const { isAuthenticated } = useAuth();
  return <Navigate to={isAuthenticated ? '/customers' : '/login'} replace />;
}

export function AppRouter() {
  return (
    <Routes>
      <Route path="/" element={<RootRedirect />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/styleguide" element={<StyleguidePage />} />
      <Route element={<ProtectedRoute />}>
        <Route path="/customers" element={<CustomerSearchPage />} />
        <Route path="/customers/:idOrReference" element={<CustomerDetailPage />} />
      </Route>
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
