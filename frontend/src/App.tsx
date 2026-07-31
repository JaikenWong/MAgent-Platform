import { lazy } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import { useAuthStore } from './stores/auth'
import AdminLayout from './layouts/AdminLayout'
import Login from './pages/Login'

const Dashboard = lazy(() => import('./pages/Dashboard'))
const Agents = lazy(() => import('./pages/Agents'))
const Bots = lazy(() => import('./pages/Bots'))
const Rules = lazy(() => import('./pages/Rules'))
const ApprovalPolicies = lazy(() => import('./pages/ApprovalPolicies'))
const Approvals = lazy(() => import('./pages/Approvals'))
const Conversations = lazy(() => import('./pages/Conversations'))
const Tasks = lazy(() => import('./pages/Tasks'))
const Settings = lazy(() => import('./pages/Settings'))
const Audit = lazy(() => import('./pages/Audit'))

function Protected({ children }: { children: JSX.Element }) {
  if (!useAuthStore.getState().isAuthed()) return <Navigate to="/login" replace />
  return children
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/" element={<Protected><AdminLayout /></Protected>}>
        <Route index element={<Dashboard />} />
        <Route path="agents" element={<Agents />} />
        <Route path="bots" element={<Bots />} />
        <Route path="rules" element={<Rules />} />
        <Route path="approval-policies" element={<ApprovalPolicies />} />
        <Route path="approvals" element={<Approvals />} />
        <Route path="conversations" element={<Conversations />} />
        <Route path="tasks" element={<Tasks />} />
        <Route path="settings" element={<Settings />} />
        <Route path="audit" element={<Audit />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}