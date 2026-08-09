import { useState } from "react";
import type { Device } from "../../types/device";

interface Props {
  initialValues?: Partial<Device>;
  onSubmit: (values: any) => void;
  loading?: boolean;
}

export default function DeviceForm({
  initialValues,
  onSubmit,
  loading = false
}: Props) {

  const [form, setForm] = useState({

    caseId: initialValues?.caseId ?? "",

    manufacturer: initialValues?.manufacturer ?? "",

    brand: initialValues?.brand ?? "",

    model: initialValues?.model ?? "",

    deviceName: initialValues?.deviceName ?? "",

    imei: initialValues?.imei ?? "",

    serialNumber: initialValues?.serialNumber ?? "",

    operatingSystem: initialValues?.operatingSystem ?? "Android",

    osVersion: initialValues?.osVersion ?? "",

    deviceType: initialValues?.deviceType ?? "",

    ownerName: initialValues?.ownerName ?? "",

    ownerContact: initialValues?.ownerContact ?? "",

    investigator: initialValues?.investigator ?? "",

    acquisitionMethod:
      initialValues?.acquisitionMethod ??
      "LOGICAL_EXTRACTION",

    acquisitionStatus:
      initialValues?.acquisitionStatus ??
      "SEIZED",

    seizedDate: initialValues?.seizedDate ?? "",

    receivedDate: initialValues?.receivedDate ?? "",

    notes: initialValues?.notes ?? ""

  });

  function change(e: React.ChangeEvent<
    HTMLInputElement |
    HTMLTextAreaElement |
    HTMLSelectElement
  >) {

    setForm({

      ...form,

      [e.target.name]: e.target.value

    });

  }

  function submit(e: React.FormEvent) {

    e.preventDefault();

    onSubmit(form);

  }

  const inputStyle =
    "w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-white";

  return (

    <form
      onSubmit={submit}
      className="space-y-6"
    >

      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-5">

        <div>

          <label className="mb-2 block text-sm text-slate-300">
            Case ID
          </label>

          <input
            name="caseId"
            value={form.caseId}
            onChange={change}
            className={inputStyle}
            required
          />

        </div>

        <div>

          <label className="mb-2 block text-sm text-slate-300">
            Manufacturer
          </label>

          <input
            name="manufacturer"
            value={form.manufacturer}
            onChange={change}
            className={inputStyle}
            required
          />

        </div>

        <div>

          <label className="mb-2 block text-sm text-slate-300">
            Brand
          </label>

          <input
            name="brand"
            value={form.brand}
            onChange={change}
            className={inputStyle}
            required
          />

        </div>

        <div>

          <label className="mb-2 block text-sm text-slate-300">
            Model
          </label>

          <input
            name="model"
            value={form.model}
            onChange={change}
            className={inputStyle}
            required
          />

        </div>

        <div>

          <label className="mb-2 block text-sm text-slate-300">
            Device Name
          </label>

          <input
            name="deviceName"
            value={form.deviceName}
            onChange={change}
            className={inputStyle}
            required
          />

        </div>

        <div>

          <label className="mb-2 block text-sm text-slate-300">
            IMEI
          </label>

          <input
            name="imei"
            value={form.imei}
            onChange={change}
            className={inputStyle}
            required
          />

        </div>

        <div>

          <label className="mb-2 block text-sm text-slate-300">
            Serial Number
          </label>

          <input
            name="serialNumber"
            value={form.serialNumber}
            onChange={change}
            className={inputStyle}
          />

        </div>

        <div>

          <label className="mb-2 block text-sm text-slate-300">
            Operating System
          </label>

          <select
            name="operatingSystem"
            value={form.operatingSystem}
            onChange={change}
            className={inputStyle}
          >

            <option>Android</option>

            <option>iOS</option>

          </select>

        </div>

        <div>

          <label className="mb-2 block text-sm text-slate-300">
            OS Version
          </label>

          <input
            name="osVersion"
            value={form.osVersion}
            onChange={change}
            className={inputStyle}
          />

        </div>

        <div>

          <label className="mb-2 block text-sm text-slate-300">
            Device Type
          </label>

          <input
            name="deviceType"
            value={form.deviceType}
            onChange={change}
            className={inputStyle}
          />

        </div>

        <div>

          <label className="mb-2 block text-sm text-slate-300">
            Owner
          </label>

          <input
            name="ownerName"
            value={form.ownerName}
            onChange={change}
            className={inputStyle}
          />

        </div>

        <div>

          <label className="mb-2 block text-sm text-slate-300">
            Contact
          </label>

          <input
            name="ownerContact"
            value={form.ownerContact}
            onChange={change}
            className={inputStyle}
          />

        </div>

        <div>

          <label className="mb-2 block text-sm text-slate-300">
            Investigator
          </label>

          <input
            name="investigator"
            value={form.investigator}
            onChange={change}
            className={inputStyle}
          />

        </div>

        <div>

          <label className="mb-2 block text-sm text-slate-300">
            Acquisition Method
          </label>

          <select
            name="acquisitionMethod"
            value={form.acquisitionMethod}
            onChange={change}
            className={inputStyle}
          >

            <option>LOGICAL_EXTRACTION</option>
            <option>FILE_SYSTEM</option>
            <option>PHYSICAL_EXTRACTION</option>
            <option>CHIP_OFF</option>
            <option>JTAG</option>

          </select>

        </div>

        <div>

          <label className="mb-2 block text-sm text-slate-300">
            Status
          </label>

          <select
            name="acquisitionStatus"
            value={form.acquisitionStatus}
            onChange={change}
            className={inputStyle}
          >

            <option>SEIZED</option>
            <option>WAITING_FOR_EXTRACTION</option>
            <option>EXTRACTION_COMPLETED</option>
            <option>UNDER_ANALYSIS</option>
            <option>REPORT_GENERATED</option>
            <option>ARCHIVED</option>

          </select>

        </div>

        <div>

          <label className="mb-2 block text-sm text-slate-300">
            Seized Date
          </label>

          <input
            type="date"
            name="seizedDate"
            value={form.seizedDate}
            onChange={change}
            className={inputStyle}
          />

        </div>

        <div>

          <label className="mb-2 block text-sm text-slate-300">
            Received Date
          </label>

          <input
            type="date"
            name="receivedDate"
            value={form.receivedDate}
            onChange={change}
            className={inputStyle}
          />

        </div>

      </div>

      <div>

        <label className="mb-2 block text-sm text-slate-300">
          Notes
        </label>

        <textarea
          rows={5}
          name="notes"
          value={form.notes}
          onChange={change}
          className={inputStyle}
        />

      </div>

      <button
        disabled={loading}
        className="rounded-lg bg-cyan-600 px-6 py-3 font-semibold text-white hover:bg-cyan-500 disabled:opacity-50"
      >
        {loading ? "Saving..." : "Save Device"}
      </button>

    </form>

  );

}