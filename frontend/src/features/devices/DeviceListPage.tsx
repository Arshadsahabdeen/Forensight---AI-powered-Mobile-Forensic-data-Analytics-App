import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import {
  Search,
  Plus,
  Smartphone,
  Shield,
  Cpu,
  HardDrive,
  Filter
} from "lucide-react";

import { Card } from "../../components/ui/Card";
import { getDevices } from "../../services/deviceService";
import type { DeviceStatus } from "../../types/device";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { deleteDevice } from "../../services/deviceService";

export function DeviceListPage() {

  const navigate = useNavigate();

  const queryClient = useQueryClient();

const deleteMutation = useMutation({

    mutationFn: deleteDevice,

    onSuccess: () => {

        queryClient.invalidateQueries({

            queryKey:["devices"]

        });

    }

});

  const [page, setPage] = useState(0);

  const [search, setSearch] = useState("");

  const [status, setStatus] = useState("");

  const [operatingSystem, setOperatingSystem] = useState("");

  const {

    data,

    isLoading,

    refetch

  } = useQuery({

    queryKey: [

      "devices",

      page,

      search,

      status,

      operatingSystem

    ],

    queryFn: () =>

      getDevices(

        page,

        10,

        search,

        status,

        operatingSystem

      )

  });

  const stats = useMemo(() => {

    if (!data)

      return {

        total: 0,

        android: 0,

        ios: 0,

        analysis: 0

      };

    const android = data.items.filter(

      d => d.operatingSystem.toLowerCase().includes("android")

    ).length;

    const ios = data.items.filter(

      d => d.operatingSystem.toLowerCase().includes("ios")

    ).length;

    const analysis = data.items.filter(

      d => d.acquisitionStatus === "UNDER_ANALYSIS"

    ).length;

    return {

      total: data.totalElements,

      android,

      ios,

      analysis

    };

  }, [data]);

  if (isLoading) {

    return (

      <Card>

        <div className="p-10 text-center text-slate-400">

          Loading devices...

        </div>

      </Card>

    );

  }

  return (

    <div className="space-y-6">

      <div className="flex items-center justify-between">

        <div>

          <h1 className="text-3xl font-bold text-white">

            Device Management

          </h1>

          <p className="text-slate-400 mt-1">

            Manage seized mobile devices and forensic acquisitions.

          </p>

        </div>

        <button

          onClick={() => navigate("/devices/new")}

          className="flex items-center gap-2 rounded-lg bg-cyan-600 px-4 py-2 font-medium text-white hover:bg-cyan-500"

        >

          <Plus size={18} />

          Add Device

        </button>

      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-5">

        <Card>

          <div className="flex justify-between">

            <div>

              <p className="text-slate-400">

                Total Devices

              </p>

              <h2 className="text-3xl font-bold text-white mt-2">

                {stats.total}

              </h2>

            </div>

            <Smartphone className="text-cyan-400" />

          </div>

        </Card>

        <Card>

          <div className="flex justify-between">

            <div>

              <p className="text-slate-400">

                Android

              </p>

              <h2 className="text-3xl font-bold text-white mt-2">

                {stats.android}

              </h2>

            </div>

            <Cpu className="text-green-400" />

          </div>

        </Card>

        <Card>

          <div className="flex justify-between">

            <div>

              <p className="text-slate-400">

                iPhone

              </p>

              <h2 className="text-3xl font-bold text-white mt-2">

                {stats.ios}

              </h2>

            </div>

            <Smartphone className="text-blue-400" />

          </div>

        </Card>

        <Card>

          <div className="flex justify-between">

            <div>

              <p className="text-slate-400">

                Under Analysis

              </p>

              <h2 className="text-3xl font-bold text-white mt-2">

                {stats.analysis}

              </h2>

            </div>

            <Shield className="text-orange-400" />

          </div>

        </Card>

      </div>

      <Card>

        <div className="grid md:grid-cols-3 gap-4">

          <div className="relative">

            <Search
              size={18}
              className="absolute left-3 top-3 text-slate-500"
            />

            <input

              value={search}

              onChange={(e) => setSearch(e.target.value)}

              placeholder="Search IMEI, Manufacturer..."

              className="w-full rounded-lg bg-slate-900 border border-slate-700 pl-10 pr-3 py-2 text-white"

            />

          </div>

          <select

            value={status}

            onChange={(e) => setStatus(e.target.value)}

            className="rounded-lg bg-slate-900 border border-slate-700 p-2 text-white"

          >

            <option value="">All Status</option>

            <option value="SEIZED">Seized</option>

            <option value="WAITING_FOR_EXTRACTION">

              Waiting Extraction

            </option>

            <option value="EXTRACTION_COMPLETED">

              Extraction Completed

            </option>

            <option value="UNDER_ANALYSIS">

              Under Analysis

            </option>

            <option value="REPORT_GENERATED">

              Report Generated

            </option>

            <option value="ARCHIVED">

              Archived

            </option>

          </select>

          <select

            value={operatingSystem}

            onChange={(e) => setOperatingSystem(e.target.value)}

            className="rounded-lg bg-slate-900 border border-slate-700 p-2 text-white"

          >

            <option value="">All OS</option>

            <option value="Android">

              Android

            </option>

            <option value="iOS">

              iOS

            </option>

          </select>

        </div>

      </Card>
            <Card>

        <div className="overflow-x-auto">

          <table className="w-full min-w-[1300px]">

            <thead className="border-b border-slate-700">

              <tr className="text-left text-slate-400 text-sm">

                <th className="py-3">Device</th>

                <th>IMEI</th>

                <th>Operating System</th>

                <th>Investigator</th>

                <th>Status</th>

                <th>Case</th>

                <th>Updated</th>

                <th className="text-center">Actions</th>

              </tr>

            </thead>

            <tbody>

              {data?.items.map((device) => (

                <tr

                  key={device.id}

                  className="border-b border-slate-800 hover:bg-slate-900/40 transition"

                >

                  <td className="py-4">

                    <div>

                      <p className="font-semibold text-white">

                        {device.deviceName}

                      </p>

                      <p className="text-sm text-slate-400">

                        {device.manufacturer} {device.model}

                      </p>

                    </div>

                  </td>

                  <td className="text-slate-300">

                    {device.imei}

                  </td>

                  <td>

                    <span className="rounded-full bg-slate-800 px-3 py-1 text-xs text-cyan-400">

                      {device.operatingSystem}

                    </span>

                  </td>

                  <td className="text-slate-300">

                    {device.investigator || "-"}

                  </td>

                  <td>

                    <span

                      className={`rounded-full px-3 py-1 text-xs font-medium

                      ${
                        device.acquisitionStatus === "SEIZED"

                          ? "bg-red-500/20 text-red-400"

                        : device.acquisitionStatus === "WAITING_FOR_EXTRACTION"

                          ? "bg-yellow-500/20 text-yellow-400"

                        : device.acquisitionStatus === "EXTRACTION_COMPLETED"

                          ? "bg-green-500/20 text-green-400"

                        : device.acquisitionStatus === "UNDER_ANALYSIS"

                          ? "bg-blue-500/20 text-blue-400"

                        : device.acquisitionStatus === "REPORT_GENERATED"

                          ? "bg-purple-500/20 text-purple-400"

                          : "bg-slate-600/20 text-slate-300"

                      }`}

                    >

                      {device.acquisitionStatus.replace(/_/g, " ")}

                    </span>

                  </td>

                  <td className="text-cyan-400 font-medium">

                    {device.caseNumber}

                  </td>

                  <td className="text-slate-400 text-sm">

                    {new Date(device.updatedAt).toLocaleDateString()}

                  </td>

                  <td>

                    <div className="flex justify-center gap-2">

                      <button

                        onClick={() => navigate(`/devices/${device.id}`)}

                        className="rounded-md bg-cyan-600 px-3 py-1 text-sm text-white hover:bg-cyan-500"

                      >

                        View

                      </button>

                      <button

                        onClick={() => navigate(`/devices/${device.id}/edit`)}

                        className="rounded-md bg-amber-500 px-3 py-1 text-sm text-black hover:bg-amber-400"

                      >

                        Edit

                      </button>

                      <button

onClick={() => {

    if(

        window.confirm(

            "Delete this device?"

        )

    ){

        deleteMutation.mutate(device.id);

    }

}}

className="rounded-md bg-red-600 px-3 py-1 text-sm text-white hover:bg-red-500"

>

Delete

</button>

                    </div>

                  </td>

                </tr>

              ))}

            </tbody>

          </table>

        </div>

      </Card>

      <div className="flex items-center justify-between">

        <button

          disabled={page === 0}

          onClick={() => setPage(page - 1)}

          className="rounded-md border border-slate-700 px-4 py-2 text-white disabled:opacity-40"

        >

          Previous

        </button>

        <p className="text-slate-400">

          Page {page + 1} of {data?.totalPages ?? 1}

        </p>

        <button

          disabled={data?.last}

          onClick={() => setPage(page + 1)}

          className="rounded-md border border-slate-700 px-4 py-2 text-white disabled:opacity-40"

        >

          Next

        </button>

      </div>

    </div>

  );

}