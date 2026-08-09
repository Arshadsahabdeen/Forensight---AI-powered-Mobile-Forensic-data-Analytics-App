import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";

import { Card } from "../../components/ui/Card";
import DeviceForm from "./DeviceForm";
import { createDevice } from "../../services/deviceService";

export function DeviceCreatePage() {

  const navigate = useNavigate();

  const queryClient = useQueryClient();

  const mutation = useMutation({

    mutationFn: createDevice,

    onSuccess: () => {

      queryClient.invalidateQueries({

        queryKey: ["devices"]

      });

      navigate("/devices");

    }

  });

  return (

    <div className="space-y-6">

      <div>

        <h1 className="text-3xl font-bold text-white">

          Register New Device

        </h1>

        <p className="mt-2 text-slate-400">

          Add a newly seized mobile device into the forensic platform.

        </p>

      </div>

      <Card>

        <DeviceForm

          loading={mutation.isPending}

          onSubmit={(values) => mutation.mutate(values)}

        />

      </Card>

    </div>

  );

}