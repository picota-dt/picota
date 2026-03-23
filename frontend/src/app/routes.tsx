import {createBrowserRouter} from "react-router";
import LandingPage from "./pages/LandingPage";
import LoginPage from "./pages/LoginPage";
import RegisterPage from "./pages/RegisterPage";
import ProfilePage from "./pages/ProfilePage";
import {AppLayout} from "./components/AppLayout";
import {CatalogPage} from "./components/CatalogPage";
import TwinDetailPage from "./pages/TwinDetailPage";

export const router = createBrowserRouter([
    {path: "/", Component: LandingPage},
    {path: "/login", Component: LoginPage},
    {path: "/register", Component: RegisterPage},
    {
        Component: AppLayout,
        children: [
            {path: "/twins", Component: CatalogPage},
            {path: "/twins/:id", Component: TwinDetailPage},
            {path: "/profile", Component: ProfilePage},
        ],
    },
]);
