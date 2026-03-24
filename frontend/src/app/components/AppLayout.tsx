import {Navigate, Outlet} from "react-router";
import {Navbar} from "./Navbar";
import {useApp} from "../context/AppContext";

export function AppLayout() {
    const {isLoggedIn, loading} = useApp();

    if (loading) {
        return (
            <div className="min-h-screen bg-[#0f1117]"/>
        );
    }
    if (!isLoggedIn) {
        return <Navigate to="/login" replace/>;
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
