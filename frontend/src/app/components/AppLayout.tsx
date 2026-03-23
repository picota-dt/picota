import {Outlet} from "react-router";
import {Navbar} from "./Navbar";

export function AppLayout() {
    return (
        <div className="min-h-screen bg-[#0f1117] flex flex-col">
            <Navbar/>
            <main className="flex-1">
                <Outlet/>
            </main>
        </div>
    );
}
