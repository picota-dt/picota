import {Navigate, Outlet, useLocation} from "react-router";
import {Navbar} from "./Navbar";
import {useApp} from "../context/AppContext";

export function AppLayout() {
    const {isLoggedIn, loading} = useApp();
    const location = useLocation();

    if (loading) {
        return (
            <div className="min-h-screen bg-[#0f1117]"/>
        );
    }
    if (!isLoggedIn) {
        const redirect = `${location.pathname}${location.search}${location.hash}`;
        return <Navigate to={`/login?redirect=${encodeURIComponent(redirect)}`} replace/>;
    }

    return (
        <div className="min-h-screen bg-[#0f1117] flex flex-col">
            <Navbar/>
            <main className="flex-1">
                <Outlet/>
            </main>
        </div>
    );
}
