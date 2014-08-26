<#if region??>
    <form class="form form-horizontal" role="form" action="/modules/region/clear" method="post">
        <label for="change">Your region - ${region}</label>
        <button id="change" type="submit" class="btn btn-link">Change</button>
    </form>
<#else>
    <form class="form" role="form" action="/modules/region" method="post">
        <div class="form-group">
            <label for="region">Choose region</label>
            <select class="form-control" id="region" name="regionId">
                <option value="moscow">Moscow</option>
                <option value="spb">Saint-Petersburg</option>
            </select>
        </div>
        <button class="btn btn-link pull-right">Choose</button>
    </form>
</#if>